"""Tender document reader, built for Pakistani public procurement PDFs.

Why a purpose-built tool rather than pattern matching over flat text:

Tender documents are not prose. They are numbered clause books ("23.1 The Bid
Security shall...") wrapped around key-value tables (bid security amounts,
delivery schedules, technical specifications). Flattening that to a wall of text
throws away exactly the structure that tells you what a number means - which is
how a naive matcher reads "eligible payments" as an eligibility rule, or a page
footer as a delivery deadline.

This reader keeps the structure and looks for facts in order of how trustworthy
the location is:

    1. a labelled table cell          - highest confidence
    2. a numbered clause              - high
    3. a labelled line ("Key: value") - medium
    4. a loose sentence               - low, and marked as low

Every extracted fact carries the page number, the clause number where there is
one, the exact text it came from, and how it was found. Nothing is ever
asserted without that evidence, because these are government and military
procurements where a confident wrong answer is worse than no answer.
"""
from __future__ import annotations

import logging
import re
import warnings
from dataclasses import dataclass, field, asdict
from typing import Optional

warnings.filterwarnings("ignore")
logging.getLogger("pdfminer").setLevel(logging.CRITICAL)
logging.getLogger("pypdf").setLevel(logging.CRITICAL)

try:
    import pdfplumber
except ImportError:
    pdfplumber = None       # type: ignore

MAX_PAGES = 120


class Confidence:
    HIGH = "high"       # found in a labelled table cell or an explicit label
    MEDIUM = "medium"   # found inside a numbered clause
    LOW = "low"         # found in loose text; shown with a caution


@dataclass
class Evidence:
    """Where a fact came from. Always populated - a fact without evidence is
    not recorded at all."""
    page: int
    quote: str
    method: str
    clause_no: Optional[str] = None
    document: str = ""

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class Fact:
    key: str
    label: str
    value: Optional[str]        # the figure or phrase, verbatim from the page
    plain: str                  # what it means, in ordinary words
    confidence: str
    evidence: Evidence

    def to_dict(self) -> dict:
        d = asdict(self)
        d["evidence"] = self.evidence.to_dict()
        return d


@dataclass
class SpecRow:
    """One line of a technical specification table."""
    name: str
    value: str
    page: int

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class Document:
    """A parsed tender PDF."""
    name: str
    pages: int = 0
    page_text: list[str] = field(default_factory=list)
    tables: list[tuple[int, list[list[Optional[str]]]]] = field(default_factory=list)
    clauses: list[tuple[int, str, str]] = field(default_factory=list)   # page, no, text
    readable: bool = True
    note: str = ""


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------

_FOOTER = re.compile(
    r"(generated document|page \d+ of \d+|©\s*\d{4}|all rights reserved)", re.I
)

# "23.1", "7.1.2", "12)" at the start of a clause.
_CLAUSE_START = re.compile(r"^\s*(\d{1,2}(?:\.\d{1,2}){1,2})\s+(?=[A-Z(\"'])")


def _strip_footers(text: str) -> str:
    return "\n".join(ln for ln in text.split("\n") if not _FOOTER.search(ln))


def parse(path: str, name: str = "") -> Document:
    """Read one PDF into pages, tables and numbered clauses."""
    doc = Document(name=name or path.rsplit("/", 1)[-1])
    if pdfplumber is None:
        doc.readable = False
        doc.note = "PDF reader not installed"
        return doc

    try:
        pdf = pdfplumber.open(path)
    except Exception as e:                      # noqa: BLE001
        doc.readable = False
        doc.note = f"could not open ({type(e).__name__})"
        return doc

    try:
        doc.pages = len(pdf.pages)
        for i, page in enumerate(pdf.pages[:MAX_PAGES], start=1):
            try:
                text = page.extract_text() or ""
            except Exception:                    # noqa: BLE001
                text = ""
            doc.page_text.append(_strip_footers(text))

            try:
                for tbl in page.extract_tables() or []:
                    if tbl and len(tbl) > 1:
                        doc.tables.append((i, tbl))
            except Exception:                    # noqa: BLE001
                pass
    finally:
        try:
            pdf.close()
        except Exception:                        # noqa: BLE001
            pass

    total = sum(len(t) for t in doc.page_text)
    if total < 200:
        doc.readable = False
        doc.note = ("no text could be read - this document is almost certainly a "
                    "scan of a printed page, so it has to be opened and read by eye")
        return doc

    doc.clauses = _split_clauses(doc.page_text)
    return doc


def _split_clauses(pages: list[str]) -> list[tuple[int, str, str]]:
    """Break the document into numbered clauses.

    A clause is the unit a tender is actually written in, so keeping clauses
    whole is what stops a number being read against the wrong rule.
    """
    out: list[tuple[int, str, str]] = []
    for pageno, text in enumerate(pages, start=1):
        current_no: Optional[str] = None
        buf: list[str] = []
        for raw in text.split("\n"):
            line = raw.strip()
            if not line:
                continue
            m = _CLAUSE_START.match(line)
            if m:
                if current_no and buf:
                    out.append((pageno, current_no, " ".join(buf).strip()))
                current_no = m.group(1)
                buf = [line[m.end():].strip()]
            elif current_no:
                buf.append(line)
        if current_no and buf:
            out.append((pageno, current_no, " ".join(buf).strip()))
    return out


# ---------------------------------------------------------------------------
# Field definitions
# ---------------------------------------------------------------------------

MONEY = r"(?:Rs\.?|PKR|Rupees)\s*[\d,]+(?:\.\d+)?(?:\s*(?:million|lac|lakh|crore))?"
PERCENT = r"\d+(?:\.\d+)?\s*(?:%|percent|per\s*cent)"
DURATION = r"\d+\s*(?:days?|weeks?|months?|years?)"


@dataclass
class FieldSpec:
    key: str
    label: str
    # Words that identify the row/clause. All must be absent from `avoid`.
    labels: list[str]
    avoid: list[str] = field(default_factory=list)
    value_pattern: Optional[str] = None
    require_value: bool = False


FIELDS: list[FieldSpec] = [
    FieldSpec(
        key="bid_security", label="Bid deposit (bid security)",
        labels=[r"bid\s*security", r"earnest\s*money", r"\bEMD\b"],
        avoid=[r"bid\s*securing\s*declaration\s*form"],
        value_pattern=f"(?:{MONEY}|{PERCENT})",
    ),
    FieldSpec(
        key="document_fee", label="Cost of the bidding papers",
        labels=[r"(?:tender|bidding)\s*document\s*(?:fee|price|cost|charges)",
                r"non-?refundable\s*(?:fee|amount|charges)"],
        value_pattern=MONEY,
    ),
    FieldSpec(
        key="estimated_cost", label="Estimated value of the work",
        labels=[r"estimated\s*(?:cost|value|price|amount)", r"approximate\s*cost"],
        value_pattern=MONEY, require_value=True,
    ),
    FieldSpec(
        key="performance", label="Guarantee if you win",
        labels=[r"performance\s*(?:guarantee|security|bond)", r"retention\s*money"],
        value_pattern=f"(?:{PERCENT}|{MONEY})",
    ),
    FieldSpec(
        key="validity", label="How long your price must hold",
        labels=[r"bid\s*validity", r"validity\s*(?:of|period)\s*(?:the\s*)?bid",
                r"bids?\s*shall\s*remain\s*valid"],
        value_pattern=DURATION,
    ),
    FieldSpec(
        key="delivery", label="How long you get to deliver",
        labels=[r"delivery\s*(?:period|time|schedule)", r"completion\s*(?:period|time)",
                r"time\s*(?:for|of)\s*completion", r"supply\s*period"],
        value_pattern=DURATION, require_value=True,
    ),
    FieldSpec(
        key="penalty", label="Penalty for being late",
        labels=[r"liquidated\s*damages", r"penalty\s*(?:for|of)\s*(?:delay|late)",
                r"late\s*delivery\s*charges"],
        value_pattern=f"(?:{PERCENT}|{MONEY})",
    ),
    FieldSpec(
        key="submission", label="Deadline for handing in your bid",
        labels=[r"(?:last\s*date|deadline|closing\s*date).{0,24}(?:submission|bids?)",
                r"bids?\s*(?:must|shall)\s*be\s*(?:submitted|received|dropped)"],
    ),
    FieldSpec(
        key="opening", label="When bids are opened",
        labels=[r"(?:bid|tender)\s*opening", r"bids?\s*(?:will|shall)\s*be\s*opened"],
    ),
    FieldSpec(
        key="eligibility", label="Who can bid",
        labels=[r"eligib(?:le|ility)\s*(?:bidders?|firms?|contractors?|suppliers?)",
                r"eligibility\s*criteria",
                r"(?:bidders?|firms?)\s*(?:must|shall)\s*(?:be\s*)?(?:registered|possess|have)"],
        avoid=[r"eligible\s*payments?"],
    ),
    FieldSpec(
        key="procedure", label="How to package your bid",
        labels=[r"single\s*stage[\s-]*two\s*envelope", r"two[\s-]*envelope",
                r"single\s*stage[\s-]*one\s*envelope", r"two\s*stage"],
    ),
]

# Plain-language templates. The VALUE always comes from the document; only the
# surrounding explanation is ours.
PLAIN = {
    "bid_security": ("You must attach a refundable deposit with your bid{value}. "
                     "You get it back if you do not win."),
    "document_fee": "You pay{value} to obtain the bidding papers. This is not refundable.",
    "estimated_cost": "The department expects this work to cost around{value}.",
    "performance": ("If you win, a further{value} is held as security until the job is "
                    "finished properly."),
    "validity": "Your quoted price must stay valid for{value}. You cannot change it in that time.",
    "delivery": "You must deliver or finish the work within{value} of receiving the order.",
    "penalty": "If you are late, {value} is cut from your payment. Do not promise a date you cannot meet.",
    "submission": "This is the moment your bid must be in. Arriving after it means it is not accepted.",
    "opening": "Bids are opened at this time. You or your representative may attend.",
    "eligibility": "These are the conditions your company must meet to be allowed to bid.",
    "procedure": "This is how your bid must be packaged.",
}


def _plain(key: str, value: Optional[str]) -> str:
    tmpl = PLAIN.get(key, "")
    if not tmpl:
        return ""
    return re.sub(r"\s{2,}", " ", tmpl.format(value=(" " + value) if _usable(value) else "")).strip()


# ---------------------------------------------------------------------------
# Extraction
# ---------------------------------------------------------------------------

def _matches(spec: FieldSpec, text: str) -> bool:
    if any(re.search(a, text, re.I) for a in spec.avoid):
        return False
    return any(re.search(p, text, re.I) for p in spec.labels)


def _value_in(spec: FieldSpec, text: str) -> Optional[str]:
    if not spec.value_pattern:
        return None
    m = re.search(spec.value_pattern, text, re.I)
    if not m:
        return None
    value = m.group(0).strip().rstrip(",.;:")
    # A pattern can still match debris such as a bare "Rs," with no number.
    # A money or duration value is only real if it contains a digit.
    return value if re.search(r"\d", value) else None


# Cells that answer a checklist rather than state a term. "Bid Security: Yes"
# tells you a deposit exists, not what it is - reporting it as the amount would
# be worse than reporting nothing.
_NON_VALUES = re.compile(
    r"^(yes|no|n/?a|nil|none|not\s*applicable|required|not\s*required|"
    r"applicable|document|attached|as\s*per.*|refer.*|see\s*.*|-+|—+)$",
    re.I,
)


def _usable(value: Optional[str]) -> bool:
    if not value:
        return False
    v = value.strip()
    return bool(v) and len(v) > 1 and not _NON_VALUES.match(v)


def _needs_value(spec: FieldSpec) -> bool:
    """A money/duration field is only worth reporting with its figure."""
    return spec.value_pattern is not None


def _from_tables(doc: Document, spec: FieldSpec) -> Optional[Fact]:
    """A labelled table cell is the most reliable place a figure can sit."""
    for pageno, table in doc.tables:
        for row in table:
            cells = [(c or "").strip() for c in row]
            if len(cells) < 2:
                continue
            label_cell = cells[0]
            if not label_cell or not _matches(spec, label_cell):
                continue
            for other in cells[1:]:
                if not other:
                    continue
                # When a field expects a figure, only a real figure counts.
                # Otherwise the cell text itself must be informative.
                value = (_value_in(spec, other) if spec.value_pattern
                         else (other if len(other) < 120 else None))
                if _usable(value):
                    return Fact(
                        key=spec.key, label=spec.label, value=value,
                        plain=_plain(spec.key, value),
                        confidence=Confidence.HIGH,
                        evidence=Evidence(
                            page=pageno,
                            quote=f"{label_cell}: {other}",
                            method="table row",
                            document=doc.name,
                        ),
                    )
    return None


def _from_clauses(doc: Document, spec: FieldSpec) -> Optional[Fact]:
    """A numbered clause is the unit the document is written in."""
    for pageno, no, text in doc.clauses:
        if not _matches(spec, text):
            continue
        value = _value_in(spec, text)
        if _needs_value(spec) and not _usable(value):
            continue
        return Fact(
            key=spec.key, label=spec.label, value=value,
            plain=_plain(spec.key, value),
            confidence=Confidence.MEDIUM,
            evidence=Evidence(
                page=pageno, quote=text[:600], method="numbered clause",
                clause_no=no, document=doc.name,
            ),
        )
    return None


def _from_labelled_lines(doc: Document, spec: FieldSpec) -> Optional[Fact]:
    """'Bid Security: Rs. 124,420' written as a plain labelled line."""
    for pageno, text in enumerate(doc.page_text, start=1):
        for line in text.split("\n"):
            line = line.strip()
            if len(line) > 200 or ":" not in line:
                continue
            label, _, rest = line.partition(":")
            if not _matches(spec, label):
                continue
            value = (_value_in(spec, rest) if spec.value_pattern
                     else (rest.strip() or None))
            if _usable(value):
                return Fact(
                    key=spec.key, label=spec.label, value=value,
                    plain=_plain(spec.key, value),
                    confidence=Confidence.HIGH,
                    evidence=Evidence(page=pageno, quote=line, method="labelled line",
                                      document=doc.name),
                )
    return None


def _from_loose_text(doc: Document, spec: FieldSpec) -> Optional[Fact]:
    """Last resort. Marked low confidence so the app can caution the reader."""
    for pageno, text in enumerate(doc.page_text, start=1):
        for sentence in re.split(r"(?<=[.;])\s+", text.replace("\n", " ")):
            sentence = sentence.strip()
            if not (30 < len(sentence) < 500) or not _matches(spec, sentence):
                continue
            value = _value_in(spec, sentence)
            if _needs_value(spec) and not _usable(value):
                continue
            return Fact(
                key=spec.key, label=spec.label, value=value,
                plain=_plain(spec.key, value),
                confidence=Confidence.LOW,
                evidence=Evidence(page=pageno, quote=sentence, method="sentence",
                                  document=doc.name),
            )
    return None


# Technical specification tables: two columns, many rows, values carrying units.
_SPEC_UNITS = re.compile(
    r"\b(nits?|hz|mm|cm|watt|w\b|volt|v\b|amp|khz|bit|pixel|scan|ip\d\d|"
    r"kg|°c|%|hours?|hrs?|cd/m|lumen|ratio|dpi|led|smd|cob)\b", re.I
)


def _spec_rows(doc: Document) -> list[SpecRow]:
    """Pull technical specification tables.

    For a display company this is often the most valuable part of the whole
    document: it is the exact screen being asked for - pitch, brightness,
    refresh rate, controller - which is what a price is built from.
    """
    rows: list[SpecRow] = []
    for pageno, table in doc.tables:
        hits = 0
        candidate: list[SpecRow] = []
        for row in table:
            cells = [(c or "").strip() for c in row if c and c.strip()]
            if len(cells) != 2:
                continue
            name, value = cells
            if not name or not value or len(name) > 70 or len(value) > 120:
                continue
            candidate.append(SpecRow(name=name, value=value, page=pageno))
            if _SPEC_UNITS.search(value) or re.search(r"\d", value):
                hits += 1
        # Require several unit-bearing rows, so ordinary two-column tables
        # (addresses, contacts) are not mistaken for a specification.
        if hits >= 4 and len(candidate) >= 4:
            rows.extend(candidate)
    return rows


@dataclass
class Extraction:
    facts: list[Fact] = field(default_factory=list)
    specs: list[SpecRow] = field(default_factory=list)
    unreadable: list[str] = field(default_factory=list)
    documents: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "facts": [f.to_dict() for f in self.facts],
            "specs": [s.to_dict() for s in self.specs],
            "unreadable": self.unreadable,
            "documents": self.documents,
            "summary": self.summary(),
        }

    def summary(self) -> str:
        if not self.facts and self.unreadable:
            return ("The documents could not be read as text. They are scanned images, "
                    "so they have to be opened and read directly.")
        if not self.facts:
            return "No standard tender terms were recognised in these documents."
        high = sum(1 for f in self.facts if f.confidence == Confidence.HIGH)
        bits = [f"{len(self.facts)} key terms found"]
        if high:
            bits.append(f"{high} taken straight from a labelled table or line")
        if self.specs:
            bits.append(f"{len(self.specs)} technical specifications listed")
        return ", ".join(bits) + "."


def extract_all(pdf_paths: dict[str, str]) -> Extraction:
    """Read every document belonging to one tender.

    pdf_paths: {display name: local file path}
    """
    result = Extraction()
    docs: list[Document] = []

    for name, path in pdf_paths.items():
        doc = parse(path, name)
        result.documents.append(name)
        if not doc.readable:
            result.unreadable.append(f"{name}: {doc.note}")
            continue
        docs.append(doc)

    for spec in FIELDS:
        best: Optional[Fact] = None
        for doc in docs:
            for finder in (_from_tables, _from_labelled_lines, _from_clauses, _from_loose_text):
                fact = finder(doc, spec)
                if fact is None:
                    continue
                # Keep the most trustworthy source across all documents.
                if best is None or _rank(fact.confidence) > _rank(best.confidence):
                    best = fact
                break
            if best and best.confidence == Confidence.HIGH:
                break
        if best:
            result.facts.append(best)

    for doc in docs:
        result.specs.extend(_spec_rows(doc))

    return result


def _rank(confidence: str) -> int:
    return {Confidence.HIGH: 3, Confidence.MEDIUM: 2, Confidence.LOW: 1}.get(confidence, 0)
