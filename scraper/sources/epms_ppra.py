"""PPRA EPMS — https://epms.ppra.gov.pk/public/tenders/active-tenders

The primary national source. Server-rendered HTML, publicly accessible with no
login, 50 rows per page, paged via ?page=N.

Deliberate choice: we do NOT use the site's own ?keyword= filter. Its matching
is naive substring — a search for "LED" returns "SCADA Enab(led)..." — so it
both floods and misses. We pull every active tender and classify locally.

Column layout verified live against the real page:
  Sr | Tender No | Tender Details | Organization Details | Status | Advertised | Closing | Actions
"""
from __future__ import annotations

import base64
import re
from datetime import datetime, timezone

from core.classify import classify, locate
from core.fetch import Fetcher
from core.models import Tender, clean, parse_date

KEY = "epms_ppra"
NAME = "PPRA Federal (EPMS)"
BASE = "https://epms.ppra.gov.pk"
LIST = BASE + "/public/tenders/active-tenders"
MAX_PAGES = 60  # safety stop; ~3000 tenders


def _cell_text(td) -> str:
    return re.sub(r"\s+", " ", td.get_text(" ", strip=True)).strip()


def _decode_pdf_path(href: str) -> str | None:
    """/pdf?file=<base64> -> the real stored path, for a readable filename."""
    m = re.search(r"[?&]file=([^&]+)", href or "")
    if not m:
        return None
    try:
        return base64.b64decode(m.group(1) + "==").decode("utf-8", "replace")
    except Exception:  # noqa: BLE001
        return None


def _parse_row(tr, now: str) -> Tender | None:
    tds = tr.find_all("td")
    if len(tds) < 7:
        return None

    tender_no = clean(_cell_text(tds[1]))

    details = tds[2]
    title_el = details.find("strong")
    title = clean(title_el.get_text(" ", strip=True)) if title_el else None
    # The badges under the title are sector / reference chips, not description.
    badges = [clean(b.get_text(" ", strip=True)) for b in details.find_all("small", class_="badge")]
    badges = [b for b in badges if b]
    # Descriptions are the <small> elements that are NOT badges.
    descs = [clean(s.get_text(" ", strip=True)) for s in details.find_all("small")
             if "badge" not in (s.get("class") or [])]
    descs = [d for d in descs if d and d != title]
    description = clean(" — ".join(dict.fromkeys(descs))) if descs else None

    org_text = _cell_text(tds[3])
    org_small = tds[3].find("small")
    organisation = clean(org_small.get_text(" ", strip=True)) if org_small else clean(org_text)
    # Remainder of the org cell is the office/location line.
    location = clean(org_text.replace(organisation or "", "")) if organisation else None

    if not title and not tender_no:
        return None

    doc_urls, detail_url = [], None
    for a in tds[7].find_all("a", href=True) if len(tds) > 7 else []:
        href = a["href"]
        full = href if href.startswith("http") else BASE + href
        if "tender-details" in href:
            detail_url = full
        elif "/pdf?file=" in href:
            doc_urls.append(full)
        # Anything else on this row (the "invoice" link) is a web page, not a
        # document. Listing it as a document made the downloader fetch HTML.

    sector = badges[0] if badges else None
    city, province = locate(location, organisation, title)
    is_smd, score, matched = classify(title, description, sector)

    return Tender(
        source=KEY,
        source_name=NAME,
        url=detail_url or (LIST + (f"?keyword={tender_no}" if tender_no else "")),
        title=title or tender_no or "(untitled)",
        organisation=organisation,
        tender_no=tender_no,
        city=city,
        province=province,
        published=parse_date(_cell_text(tds[5])),
        closing=parse_date(_cell_text(tds[6])),
        # EPMS does not print an estimated value on the listing — left blank
        # rather than invented. It sometimes appears on the detail page.
        value=None,
        category=sector,
        description=description,
        doc_urls=doc_urls,
        is_smd=is_smd,
        smd_score=score,
        matched_terms=matched,
        tags=[b for b in badges if b != sector],
        first_seen=now,
        last_seen=now,
        raw_row=_cell_text(tr)[:1200],
    )


def scrape(fetcher: Fetcher | None = None, max_pages: int = MAX_PAGES) -> list[Tender]:
    f = fetcher or Fetcher()
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    out: list[Tender] = []
    seen_pages: set[str] = set()

    for page in range(1, max_pages + 1):
        url = f"{LIST}?page={page}"
        soup = f.soup(url, referer=LIST)
        table = soup.find("table")
        if not table or not table.find("tbody"):
            break
        rows = table.find("tbody").find_all("tr")
        if not rows:
            break
        # Guard against a site that ignores ?page and re-serves page 1.
        sig = _cell_text(rows[0])[:200]
        if sig in seen_pages:
            break
        seen_pages.add(sig)

        for tr in rows:
            t = _parse_row(tr, now)
            if t:
                out.append(t)

        if len(rows) < 50:
            break
    return out


def fetch_document(f: Fetcher, url: str) -> tuple[str, bytes] | None:
    """Download one tender document. Returns (suggested_filename, bytes)."""
    path = _decode_pdf_path(url)
    name = (path or url).rsplit("/", 1)[-1]
    if not name.lower().endswith(".pdf"):
        name += ".pdf"
    r = f.get(url, referer=BASE)
    if not r.content.startswith(b"%PDF"):
        return None          # not a PDF (login wall / html error) — don't store a lie
    return re.sub(r"[^A-Za-z0-9._-]+", "_", name), r.content


# ---------------------------------------------------------------------------
# Detail-page enrichment
# ---------------------------------------------------------------------------
# The listing gives us enough to spot a tender; the detail page gives us enough
# to BID on one: office address, contact person and email (i.e. where the bid
# actually goes), bid security, procedure, and the real tender PDFs.
#
# It also carries corrigenda. This matters: the Bahria SMD tender's listing says
# it closes 15 Sep, but corrigendum #1 moved it to 09 Sep. Trusting the listing
# alone would miss the deadline by six days, so a corrigendum date always wins.

_LABELS = {
    "Organization Name": "organisation",
    "Office Name": "office_name",
    "Office Address": "office_address",
    "City": "city",
    "Contact Person": "contact_person",
    "Contact Email": "contact_email",
    "Contact Number": "contact_phone",
    "Tender Type": "tender_type",
    "Tender No / Reference No / Tender Inquiry No": "reference_no",
    "Procurement Category": "procurement_category",
    "Procurement Procedure": "procurement_procedure",
    "Sector": "sector",
    "Tender Nature": "tender_nature",
    "Description": "full_description",
    "Advertisement Date": "advertisement_date",
    "Opening Time": "opening_time",
    "Bid Security": "bid_security",
    "Bid Validity": "bid_validity",
    "Method": "method",
    "Workflow Type": "workflow_type",
}


def _page_lines(soup) -> list[str]:
    for x in soup(["script", "style"]):
        x.decompose()
    text = soup.get_text("\n", strip=True)
    return [re.sub(r"[ \t]+", " ", ln).strip() for ln in text.split("\n") if ln.strip()]


def _field_map(lines: list[str]) -> dict[str, str]:
    """Labels sit on their own line, value on the next. Verified against the
    live page. A label with no following value yields nothing, not a blank."""
    out: dict[str, str] = {}
    for i, ln in enumerate(lines):
        key = ln.rstrip(":").strip()
        slug = _LABELS.get(key)
        if slug and i + 1 < len(lines):
            nxt = lines[i + 1].rstrip(":").strip()
            if nxt and nxt.rstrip(":") not in _LABELS:
                out.setdefault(slug, nxt)
    return out


def enrich(f: Fetcher, t: Tender) -> Tender:
    """Fill a tender from its detail page. Never overwrites a good value with
    a blank one, and records the PDF links we can actually download."""
    if not t.url or "tender-details" not in t.url:
        return t
    soup = f.soup(t.url, referer=LIST)
    lines = _page_lines(soup)
    fm = _field_map(lines)
    joined = "\n".join(lines)

    t.organisation = clean(fm.get("organisation")) or t.organisation
    t.city = clean(fm.get("city")) or t.city
    t.category = clean(fm.get("sector")) or t.category
    t.description = clean(fm.get("full_description")) or t.description
    t.value = clean(fm.get("bid_security"))          # verbatim; this is bid security, not contract value
    t.published = parse_date(fm.get("advertisement_date")) or t.published

    if not t.province and t.city:
        _, t.province = locate(t.city)

    # Closing: "September 15, 2026 at 11:00 AM" -> date + separate time.
    m = re.search(r"Closing Date & Time\n(.+?)\n(?:at\n(.+?)\n)?", joined)
    if m:
        t.closing = parse_date(m.group(1)) or t.closing

    # A corrigendum supersedes the advertised closing date.
    corr = re.findall(r"Corrigendum #\d+.*?Closing Date:\n([^\n]+)", joined, re.S)
    revised = [parse_date(c) for c in corr]
    revised = [d for d in revised if d]
    if revised:
        newest = max(revised) if len(revised) > 1 else revised[-1]
        if newest != t.closing:
            t.tags.append(f"corrigendum: closing moved from {t.closing} to {newest}")
        t.closing = newest

    extra = {k: clean(v) for k, v in fm.items()
             if k in ("office_name", "office_address", "contact_person", "contact_email",
                      "contact_phone", "tender_type", "reference_no", "procurement_category",
                      "procurement_procedure", "tender_nature", "opening_time",
                      "bid_validity", "method")}
    t.detail = {k: v for k, v in extra.items() if v}

    docs = []
    for a in soup.find_all("a", href=True):
        if "/pdf?file=" in a["href"]:
            docs.append(BASE + a["href"] if a["href"].startswith("/") else a["href"])
    if docs:
        t.doc_urls = list(dict.fromkeys(docs))

    # Re-classify with the fuller text now available.
    t.is_smd, t.smd_score, t.matched_terms = classify(
        t.title, t.description, t.category, fm.get("procurement_category"))
    return t
