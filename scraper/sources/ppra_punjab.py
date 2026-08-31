"""PPRA Punjab — https://eproc.punjab.gov.pk/Admin_Tender_Search.aspx

Punjab does NOT publish through federal EPMS; it runs its own ASP.NET WebForms
portal with a Telerik RadGrid (~1,300 active tenders, 27 pages).

Scraping it means speaking WebForms: carry __VIEWSTATE / __EVENTVALIDATION
forward on every request and drive paging with the RadGrid command
    __EVENTARGUMENT = FireCommand:<grid>;Page$N
rather than the per-page ctlNN links, which only exist for the visible window
of page numbers and shift as you move through the set.
"""
from __future__ import annotations

import re
from datetime import datetime, timezone

from bs4 import BeautifulSoup

from core.classify import classify, locate
from core.fetch import Fetcher
from core.models import Tender, clean, parse_date

KEY = "ppra_punjab"
NAME = "PPRA Punjab"
BASE = "https://eproc.punjab.gov.pk"
LEGACY_TLS = True          # this host needs the legacy cipher suite
SEARCH = BASE + "/Admin_Tender_Search.aspx"
GRID = "ctl00$ContentPlaceHolderSRIS$rdgrdManageTender$ctl00"
GRID_ID = "ctl00_ContentPlaceHolderSRIS_rdgrdManageTender_ctl00"


def _form_data(soup: BeautifulSoup) -> dict[str, str]:
    """Every control the form would post, not just the hidden ones.

    This distinction matters: posting only __VIEWSTATE/__EVENTVALIDATION gets a
    200 back with an EMPTY grid, because ASP.NET rebuilds the page without the
    search criteria. Posting the full control set returns the real next page.
    """
    data: dict[str, str] = {}
    form = soup.find("form")
    if not form:
        return data
    for inp in form.find_all("input"):
        name = inp.get("name")
        if not name:
            continue
        kind = (inp.get("type") or "text").lower()
        if kind == "submit":
            continue
        if kind in ("checkbox", "radio") and not inp.has_attr("checked"):
            continue
        data[name] = inp.get("value", "")
    for sel in form.find_all("select"):
        name = sel.get("name")
        if not name:
            continue
        opt = sel.find("option", selected=True) or sel.find("option")
        data[name] = opt.get("value", "") if opt else ""
    for ta in form.find_all("textarea"):
        if ta.get("name"):
            data[ta["name"]] = ta.get_text()
    return data


def _next_page_button(soup: BeautifulSoup) -> tuple[str, str] | None:
    """The pager's "Next Page" submit button, as (name, value).

    Walking Next beats clicking page numbers: the pager only renders a window
    of ten numbers before a "..." link, so number-clicking silently stops at
    page 10 of 27 - which is exactly how this scraper first lost two thirds of
    Punjab. Next always exists until the last page.
    """
    for inp in soup.find_all("input", type="submit"):
        if (inp.get("title") or "").strip().lower() == "next page":
            name = inp.get("name")
            if name:
                return name, inp.get("value", "")
    return None


def _is_last_page(soup: BeautifulSoup) -> bool:
    """RadGrid disables Next on the final page."""
    for inp in soup.find_all("input", type="submit"):
        if (inp.get("title") or "").strip().lower() == "next page":
            return inp.has_attr("disabled")
    return True


def _rows(soup: BeautifulSoup) -> list[list[str]]:
    """Data rows of the RadGrid. Header, filter and pager rows carry other
    classes, so keying on rgRow/rgAltRow keeps only real tenders."""
    grid = soup.find("table", id=GRID_ID)
    if not grid:
        return []
    out = []
    for tr in grid.find_all("tr"):
        cls = " ".join(tr.get("class") or [])
        if "rgRow" not in cls and "rgAltRow" not in cls:
            continue
        cells = [re.sub(r"\s+", " ", td.get_text(" ", strip=True)).strip()
                 for td in tr.find_all("td")]
        if len(cells) >= 6:
            out.append(cells)
    return out


def _to_tender(cells: list[str], now: str) -> Tender | None:
    # Verified column order: Type | Title | Category | Publish | Close | Department [| Status]
    kind, title, category, published, closing, department = cells[:6]
    status = cells[6] if len(cells) > 6 else None

    title = clean(title)
    if not title:
        return None
    department = clean(department)
    city, province = locate(department, title)
    is_smd, score, matched = classify(title, category, department)

    return Tender(
        source=KEY,
        source_name=NAME,
        url=SEARCH,          # portal has no stable per-tender permalink
        title=title,
        organisation=department,
        tender_no=None,      # not exposed on the Punjab grid
        city=city,
        province=province or "Punjab",
        published=parse_date(published),
        closing=parse_date(closing),
        value=None,
        category=clean(category),
        description=None,
        is_smd=is_smd,
        smd_score=score,
        matched_terms=matched,
        tags=[t for t in (clean(kind), clean(status)) if t],
        first_seen=now,
        last_seen=now,
        raw_row=" | ".join(cells)[:1200],
    )


def scrape(fetcher: Fetcher | None = None, max_pages: int = 40) -> list[Tender]:
    f = fetcher or Fetcher(legacy_tls=True)
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")

    soup = f.soup(SEARCH)
    out: list[Tender] = []
    seen: set[str] = set()

    for page in range(1, max_pages + 1):
        rows = _rows(soup)
        if not rows:
            break
        sig = "|".join(rows[0])[:200]
        if sig in seen:
            break
        seen.add(sig)

        for cells in rows:
            t = _to_tender(cells, now)
            if t:
                out.append(t)

        nxt = _next_page_button(soup)
        if not nxt or _is_last_page(soup):
            break
        data = _form_data(soup)
        data["__EVENTTARGET"] = ""
        data["__EVENTARGUMENT"] = ""
        data[nxt[0]] = nxt[1]          # submit buttons post as name=value
        r = f.s.post(SEARCH, data=data, timeout=f.timeout,
                     headers={"Referer": SEARCH, "Origin": BASE})
        r.raise_for_status()
        soup = BeautifulSoup(r.text, "lxml")
    return out
