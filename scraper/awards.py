"""Contract awards — who won, and for how much.

This is the part that tells you what to bid. A tender says what a department
wants; an award says what they actually paid and which firm they paid it to.
Across enough awards that becomes a price history for your own market, plus a
list of the competitors who keep beating you.

Source: PPRA's public contract register, which every procuring agency is
required to publish. Each entry carries the buyer, the work, the winning firm,
the contract value and the letter of award.

Values are stored exactly as PPRA prints them, and separately as a number where
one can be read cleanly. Nothing is estimated: a contract whose value is given
only as a band ("< Rs. 50 Million") keeps the band and gets no number, because
inventing a figure here would corrupt the very thing this file exists for.
"""
from __future__ import annotations

import argparse
import io
import json
import re
import sys
from dataclasses import dataclass, asdict, field
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from core.classify import classify, locate     # noqa: E402
from core.fetch import Fetcher                 # noqa: E402
from core.models import clean, parse_date      # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "awards.json"

BASE = "https://epms.ppra.gov.pk"
LIST = BASE + "/public/contracts"


@dataclass
class Award:
    contract_no: str | None
    reference: str | None
    title: str | None
    organisation: str | None
    office: str | None
    winner: str | None
    value_text: str | None          # verbatim, exactly as PPRA printed it
    value_pkr: int | None           # parsed only when unambiguous
    value_band: str | None          # e.g. "< Rs. 50 Million", when that is all there is
    awarded: str | None
    city: str | None
    province: str | None
    url: str | None
    doc_url: str | None
    is_smd: bool = False
    smd_score: int = 0
    matched_terms: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return asdict(self)


def _cell(td) -> str:
    return re.sub(r"\s+", " ", td.get_text(" ", strip=True)).strip()


# "M/S. Galaxy Cleaning Services", "M/s ABC Traders", "Messrs XYZ (Pvt) Ltd"
_WINNER = re.compile(
    r"\b(?:M\s*/\s*[Ss]\.?|Messrs\.?|Mssrs\.?)\s*([A-Z][^,;]{2,70}?)"
    r"(?=\s*(?:,|;|\.\s|$|at\s|for\s|amounting))",
)

# A firm name should not run into the next field. These are the words that
# signal the details cell has moved on to something else - without this, one
# winner came out as "Top Quality National Tender: TS0000006".
_WINNER_STOP = re.compile(
    r"\b(?:National\s+Tender|Tender\s*(?:No|Notice|:)|Contract\s*No|Ref\b|PCN-|"
    r"TS\d{5,}|Rs\.?\s*[\d,]|amounting|dated\b|w\.?e\.?f\b)", re.I,
)

_MONEY = re.compile(r"(?:Rs\.?|PKR)\s*([\d,]+(?:\.\d+)?)", re.I)
_BAND = re.compile(r"[<>]\s*Rs\.?\s*[\d,.]+\s*(?:million|billion|crore|lac|lakh)?", re.I)


def _winner_from(text: str) -> str | None:
    m = _WINNER.search(text)
    if not m:
        return None
    name = m.group(1)
    # Cut at the first word that belongs to the next field rather than the name.
    stop = _WINNER_STOP.search(name)
    if stop:
        name = name[: stop.start()]
    name = clean(name)
    if not name or len(name) > 70 or len(name) < 3:
        return None
    # A name that is mostly digits is a reference number that slipped through.
    letters = sum(c.isalpha() for c in name)
    return name if letters >= max(3, len(name) // 2) else None


def _value_from(text: str) -> tuple[int | None, str | None]:
    """Returns (exact rupees, band). Only one of them is normally present."""
    band = _BAND.search(text)
    m = _MONEY.search(text)
    exact = None
    if m:
        try:
            digits = m.group(1).replace(",", "")
            exact = int(float(digits))
        except ValueError:
            exact = None
    # A figure under a lakh in a contract register is almost always a typo or a
    # fragment of a reference number, not a contract value.
    if exact is not None and exact < 10_000:
        exact = None
    return exact, clean(band.group(0)) if band else None


def _parse_row(tds) -> Award | None:
    if len(tds) < 6:
        return None

    contract_cell = _cell(tds[1])
    details = _cell(tds[2])
    organisation = clean(_cell(tds[3]))
    value_text = clean(_cell(tds[4]))
    awarded = parse_date(_cell(tds[5]))

    contract_no = None
    m = re.search(r"(PCN-[\w-]+)", contract_cell)
    if m:
        contract_no = m.group(1)
    reference = None
    m = re.search(r"Ref:\s*(.+)$", contract_cell)
    if m:
        reference = clean(m.group(1))

    winner = _winner_from(details)

    # The details cell repeats the organisation, then the tender title, then the
    # winner. Stripping the parts we already have leaves the title.
    title = details
    if organisation:
        title = title.replace(organisation, " ")
    if winner:
        title = re.sub(r"\bM\s*/\s*[Ss]\.?\s*" + re.escape(winner) + r".*$", " ", title)
    title = clean(title)

    value_pkr, band = _value_from(value_text or "")

    detail_url = None
    doc_url = None
    for td in tds:
        for a in td.find_all("a", href=True):
            href = a["href"]
            full = href if href.startswith("http") else BASE + href
            if "contract-details" in href:
                detail_url = full
            elif "/pdf?file=" in href:
                doc_url = full

    is_smd, score, matched = classify(title, details)
    city, province = locate(organisation, _cell(tds[3]))

    return Award(
        contract_no=contract_no,
        reference=reference,
        title=title,
        organisation=organisation,
        office=clean(_cell(tds[3])),
        winner=winner,
        value_text=value_text,
        value_pkr=value_pkr,
        value_band=band,
        awarded=awarded,
        city=city,
        province=province,
        url=detail_url,
        doc_url=doc_url,
        is_smd=is_smd,
        smd_score=score,
        matched_terms=matched,
    )


def scrape(f: Fetcher, max_pages: int = 40) -> list[Award]:
    out: list[Award] = []
    seen: set[str] = set()

    for page in range(1, max_pages + 1):
        url = LIST if page == 1 else f"{LIST}?page={page}"
        soup = f.soup(url, referer=LIST)
        table = soup.find("table")
        if not table or not table.find("tbody"):
            break
        rows = table.find("tbody").find_all("tr")
        if not rows:
            break

        sig = _cell(rows[0])[:200]
        if sig in seen:
            break
        seen.add(sig)

        for tr in rows:
            a = _parse_row(tr.find_all("td"))
            if a and (a.contract_no or a.title):
                out.append(a)

        if len(rows) < 50:
            break
    return out


def price_history(awards: list[Award]) -> list[dict]:
    """What similar work has actually gone for.

    Only awards with an exact figure are used - a band tells you nothing about
    a price. Grouped by the words that describe the work, so "LED screen"
    awards can be compared against each other rather than against janitorial
    contracts.
    """
    groups: dict[str, list[Award]] = {}
    for a in awards:
        if a.value_pkr is None or not a.matched_terms:
            continue
        for term in a.matched_terms:
            groups.setdefault(term, []).append(a)

    out = []
    for term, items in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        values = sorted(x.value_pkr for x in items if x.value_pkr)
        if len(values) < 2:
            continue
        out.append({
            "term": term,
            "awards": len(values),
            "low": values[0],
            "median": values[len(values) // 2],
            "high": values[-1],
        })
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-pages", type=int, default=40)
    args = ap.parse_args()

    f = Fetcher(delay=0.9, legacy_tls=True)
    print("Collecting contract awards...")
    awards = scrape(f, args.max_pages)

    with_value = sum(1 for a in awards if a.value_pkr)
    with_winner = sum(1 for a in awards if a.winner)
    smd = [a for a in awards if a.is_smd]

    payload = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "count": len(awards),
        "with_value": with_value,
        "with_winner": with_winner,
        "smd_count": len(smd),
        "price_history": price_history(awards),
        "awards": [a.to_dict() for a in awards],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"\n{len(awards)} awards  ·  {with_value} with an exact value  ·  "
          f"{with_winner} with a named winner  ·  {len(smd)} SMD/LED")
    for a in smd[:10]:
        print(f"  [{a.awarded}] {(a.title or '')[:46]:48} "
              f"{(a.winner or 'winner not named')[:26]:28} {a.value_text or ''}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
