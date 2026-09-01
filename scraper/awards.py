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
EVAL_LIST = BASE + "/public/evaluations"


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

# Words that begin the buying organisation rather than the firm's own name.
# Government buyers in Pakistan almost always start with one of these.
_ORG_TAIL = re.compile(
    r"\s+(?=(?:Ministry|Government|Govt|Department|Directorate|Office\s+of|"
    r"Pakistan\s+(?:Railways|Post|Navy|Army|Air)|Sui\s+Northern|Sui\s+Southern|"
    r"National\s+(?:Bank|Highway|University)|Punjab|Sindh|Balochistan|"
    r"Khyber|University\s+of|Cantonment|Provincial|Federal|Higher\s+Education|"
    r"\w+\s+Electric\s+Supply|\w+\s+Power\s+Company|WAPDA|NTDC|PESCO|LESCO|"
    r"MEPCO|IESCO|GEPCO|FESCO|HESCO|SEPCO|QESCO|TESCO|KE))",
    re.I,
)

_MONEY = re.compile(r"(?:Rs\.?|PKR)\s*([\d,]+(?:\.\d+)?)", re.I)
_BAND = re.compile(r"[<>]\s*Rs\.?\s*[\d,.]+\s*(?:million|billion|crore|lac|lakh)?", re.I)


# A bare city is not a firm. These leak in when a name ends with its city and
# the cell runs on; without this filter "Lahore" came out as the most frequent
# lowest bidder in the country.
_PLACES = {
    "lahore", "karachi", "islamabad", "rawalpindi", "faisalabad", "multan",
    "gujranwala", "peshawar", "quetta", "sialkot", "hyderabad", "sukkur",
    "bahawalpur", "sargodha", "abbottabad", "mardan", "gujrat", "sahiwal",
    "okara", "jhelum", "kasur", "khanewal", "layyah", "vehari", "attock",
    "chakwal", "mianwali", "bhakkar", "nowshera", "swat", "kohat", "bannu",
    "pakistan", "punjab", "sindh", "balochistan",
}


# Placeholders that agencies actually type into the bidder field. These are
# real values in PPRA's data, not parsing errors - "abc" was the single most
# frequent "firm" in the country before this filter.
_JUNK_NAMES = {
    "abc", "abcd", "xyz", "test", "testing", "na", "n/a", "nil", "none",
    "aaa", "bbb", "asd", "asdf", "dummy", "sample",
}


def _is_junk_name(name: str) -> bool:
    return name.strip().strip(".").lower() in _JUNK_NAMES


def _is_place_only(name: str) -> bool:
    words = [w for w in re.split(r"[^A-Za-z]+", name) if w]
    return bool(words) and all(w.lower() in _PLACES for w in words)


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


@dataclass
class Evaluation:
    """A bid evaluation report.

    Richer than an award for working out who you are up against: it names how
    many firms bid and which was lowest. Five times more of these are published
    than contract awards.
    """
    evaluation_no: str | None
    tender_no: str | None
    reference: str | None
    title: str | None
    organisation: str | None
    bid_count: int | None
    lowest: list[str] = field(default_factory=list)
    sector: str | None = None
    evaluation_type: str | None = None
    advertised: str | None = None
    city: str | None = None
    province: str | None = None
    url: str | None = None
    is_smd: bool = False
    smd_score: int = 0
    matched_terms: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return asdict(self)


def _after(label: str, text: str, stop: tuple[str, ...]) -> str | None:
    """Value following a label in a run-together detail line."""
    m = re.search(re.escape(label) + r"\s*(.+)", text)
    if not m:
        return None
    value = m.group(1)
    cuts = [value.find(s) for s in stop if value.find(s) > 0]
    if cuts:
        value = value[: min(cuts)]
    return clean(value)


def _parse_eval(main_tds, detail_text: str) -> Evaluation | None:
    if len(main_tds) < 5:
        return None

    ids = _cell(main_tds[2])
    details = _cell(main_tds[3])

    evaluation_no = None
    m = re.search(r"(EVL\d+)", ids)
    if m:
        evaluation_no = m.group(1)
    tender_no = None
    m = re.search(r"(TS\d+E?)", ids)
    if m:
        tender_no = m.group(1)
    reference = _after("Tender Ref#:", ids, ("Tender", "EVL"))

    organisation = _after("Organization", detail_text, ("Evaluation Type", "Tender"))
    evaluation_type = _after("Evaluation Type", detail_text, ("Tender", "Organization"))

    bid_count = None
    m = re.search(r"(\d+)\s*Bids?", details, re.I)
    if m:
        bid_count = int(m.group(1))

    lowest: list[str] = []
    m = re.search(r"Lowest:\s*(.+)$", details, re.I)
    if m:
        tail = m.group(1)
        # The cell runs the buyer's name on after the winning firm with no
        # separator, so "ArwenTech Ministry of Finance" comes through as one
        # name. Cutting at the buyer restores the firm.
        if organisation:
            idx = tail.find(organisation)
            if idx > 0:
                tail = tail[:idx]
        tail = _ORG_TAIL.split(tail)[0]
        # Split only on a clear marker that a SECOND firm is being named.
        # Splitting on any comma turned "Al Khair Enterprises Multan, Lahore"
        # into two bidders and produced "Lahore" as the most frequent firm in
        # the country.
        for part in re.split(r",\s*(?=(?:M\s*/\s*[Ss]\.?|Messrs\.?)\s)", tail):
            name = clean(part)
            if not name or not (2 < len(name) <= 80):
                continue
            if _is_place_only(name) or _is_junk_name(name):
                continue
            # Normalise the honorific so the same firm groups together.
            name = re.sub(r"^(?:M\s*/\s*[Ss]\.?|Messrs\.?)\s*", "", name).strip()
            if name and not _is_junk_name(name):
                lowest.append(name)

    title = details
    if m:
        title = title[: m.start()]
    title = clean(re.sub(r"\d+\s*Bids?", " ", title))

    url = None
    for a in main_tds[-1].find_all("a", href=True) if main_tds else []:
        if "evaluation-details" in a["href"]:
            url = BASE + a["href"] if a["href"].startswith("/") else a["href"]
    if url is None:
        for td in main_tds:
            for a in td.find_all("a", href=True):
                if "evaluation-details" in a["href"]:
                    url = BASE + a["href"] if a["href"].startswith("/") else a["href"]

    is_smd, score, matched = classify(title, details)
    city, province = locate(organisation)

    return Evaluation(
        evaluation_no=evaluation_no,
        tender_no=tender_no,
        reference=reference,
        title=title,
        organisation=organisation,
        bid_count=bid_count,
        lowest=lowest,
        sector=None,
        evaluation_type=evaluation_type,
        advertised=parse_date(_cell(main_tds[4])),
        city=city,
        province=province,
        url=url,
        is_smd=is_smd,
        smd_score=score,
        matched_terms=matched,
    )


def scrape_evaluations(f: Fetcher, max_pages: int = 40) -> list[Evaluation]:
    out: list[Evaluation] = []
    seen: set[str] = set()

    for page in range(1, max_pages + 1):
        url = EVAL_LIST if page == 1 else f"{EVAL_LIST}?page={page}"
        soup = f.soup(url, referer=EVAL_LIST)
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

        # Each entry is two rows: the summary, then a detail row beneath it.
        pending = None
        for tr in rows:
            classes = " ".join(tr.get("class") or [])
            if "cb-rt-detail" in classes:
                if pending is not None:
                    e = _parse_eval(pending, _cell(tr))
                    if e:
                        out.append(e)
                    pending = None
            else:
                if pending is not None:
                    e = _parse_eval(pending, "")
                    if e:
                        out.append(e)
                pending = tr.find_all("td")
        if pending is not None:
            e = _parse_eval(pending, "")
            if e:
                out.append(e)
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
    print("Collecting evaluation reports...")
    evaluations = scrape_evaluations(f, args.max_pages)

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
        "evaluation_count": len(evaluations),
        "awards": [a.to_dict() for a in awards],
        "evaluations": [e.to_dict() for e in evaluations],
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
