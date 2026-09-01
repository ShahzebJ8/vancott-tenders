"""EPADS v2.0 — https://epads.gov.pk

The newer federal e-procurement portal. Despite both being federal PPRA, this
carries a COMPLETELY different set of tenders from EPMS: a sample of 100 EPADS
titles matched zero of the 2,994 tenders already collected. Cantonment boards,
state enterprises and many autonomous bodies publish here and nowhere else.

Listing is server-rendered, 100 rows per page, paged with ?page=N.

One quirk worth knowing: the listing prints the deadline as time REMAINING
("Closing On: 11h 19m Left") rather than a date, because the page counts down
in the browser. We convert that to a real date using the time of the scrape.
That is a derived value, so it is marked as such rather than presented as
something the portal published.
"""
from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone

from core.classify import classify, locate
from core.fetch import Fetcher
from core.models import Tender, clean, parse_date

KEY = "epads"
NAME = "EPADS v2.0"
BASE = "https://epads.gov.pk"
LIST = BASE + "/"
LEGACY_TLS = True          # this host needs the legacy cipher suite
MAX_PAGES = 40


def _cell(td) -> str:
    return re.sub(r"\s+", " ", td.get_text(" ", strip=True)).strip()


def _parse_published(status: str) -> str | None:
    """'Published On: Monday, August 17, 2026 04:00 PM' -> 2026-08-17."""
    m = re.search(r"Published On:\s*(?:\w+,\s*)?([A-Za-z]+ \d{1,2}, \d{4})", status)
    return parse_date(m.group(1)) if m else None


def _parse_closing(status: str, now: datetime) -> tuple[str | None, bool]:
    """Turn 'Closing On: 3d 11h 19m Left' into a date.

    Returns (iso_date, is_derived). is_derived is True whenever the date came
    from a countdown rather than a printed date, so the UI can be honest about
    where it came from.
    """
    m = re.search(r"Closing On:\s*(.+?)(?:Left|$)", status, re.I)
    if not m:
        return None, False
    text = m.group(1)

    # The portal sometimes prints a real date instead of a countdown.
    printed = parse_date(text)
    if printed:
        return printed, False

    days = re.search(r"(\d+)\s*d", text)
    hours = re.search(r"(\d+)\s*h", text)
    mins = re.search(r"(\d+)\s*m", text)
    if not (days or hours or mins):
        return None, False

    delta = timedelta(
        days=int(days.group(1)) if days else 0,
        hours=int(hours.group(1)) if hours else 0,
        minutes=int(mins.group(1)) if mins else 0,
    )
    return (now + delta).date().isoformat(), True


def _parse_row(tds, now: datetime, now_iso: str) -> Tender | None:
    if len(tds) < 5:
        return None

    ref = clean(_cell(tds[1]))
    title = clean(_cell(tds[2]))
    status = _cell(tds[3])
    type_proc = clean(_cell(tds[4]))

    if not title:
        return None

    detail_url = None
    for td in tds:
        for a in td.find_all("a", href=True):
            if "/procurements/" in a["href"]:
                detail_url = a["href"] if a["href"].startswith("http") else BASE + a["href"]
                break
        if detail_url:
            break

    closing, derived = _parse_closing(status, now)
    is_smd, score, matched = classify(title, type_proc)
    city, province = locate(title)

    tags = []
    if derived:
        tags.append("closing date calculated from the portal countdown")

    return Tender(
        source=KEY,
        source_name=NAME,
        url=detail_url or LIST,
        title=title,
        # The listing does not name the buying department; it is on the detail
        # page. Left blank rather than guessed from the title.
        organisation=None,
        tender_no=ref,
        city=city,
        province=province,
        published=_parse_published(status),
        closing=closing,
        value=None,
        category=clean(type_proc),
        description=None,
        # Bidding documents sit at a predictable path once the reference is known.
        doc_urls=[f"https://pa.epads.gov.pk/procurement/SBD/{ref}/bidding-document.pdf"] if ref else [],
        is_smd=is_smd,
        smd_score=score,
        matched_terms=matched,
        tags=tags,
        first_seen=now_iso,
        last_seen=now_iso,
        raw_row=(" | ".join(_cell(td) for td in tds))[:1200],
    )


def scrape(fetcher: Fetcher | None = None, max_pages: int = MAX_PAGES) -> list[Tender]:
    f = fetcher or Fetcher(legacy_tls=True)
    now = datetime.now(timezone.utc)
    now_iso = now.isoformat(timespec="seconds")

    out: list[Tender] = []
    seen: set[str] = set()

    for page in range(1, max_pages + 1):
        url = LIST if page == 1 else f"{LIST}?page={page}"
        soup = f.soup(url, referer=LIST)
        table = soup.find("table")
        if not table:
            break
        body = table.find("tbody")
        rows = body.find_all("tr") if body else table.find_all("tr")[1:]
        if not rows:
            break

        # Guard against a portal that ignores ?page and re-serves page 1.
        sig = _cell(rows[0])[:200]
        if sig in seen:
            break
        seen.add(sig)

        for tr in rows:
            t = _parse_row(tr.find_all("td"), now, now_iso)
            if t:
                out.append(t)

        if len(rows) < 100:
            break
    return out
