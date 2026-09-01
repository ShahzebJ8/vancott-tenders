"""Canonical tender record. Every field is either scraped verbatim or left None.

HARD RULE: no field is ever inferred, guessed, or filled with a plausible
default. If the source does not state it, it stays None and the app shows
"not stated" rather than a number we invented.
"""
from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field, asdict
from datetime import date, datetime
from typing import Optional


@dataclass
class Tender:
    # --- identity -------------------------------------------------------
    source: str                      # source key, e.g. "ppra_federal"
    source_name: str                 # human label, e.g. "PPRA Federal"
    url: str                         # canonical detail/listing URL

    # --- as published ---------------------------------------------------
    title: str
    organisation: Optional[str] = None      # procuring agency, verbatim
    tender_no: Optional[str] = None         # reference/advert number, verbatim
    city: Optional[str] = None
    province: Optional[str] = None
    published: Optional[str] = None         # ISO date, parsed from source only
    closing: Optional[str] = None           # ISO date/datetime, source only
    value: Optional[str] = None             # estimated cost, verbatim string
    category: Optional[str] = None          # source's own category, verbatim
    description: Optional[str] = None

    # --- documents ------------------------------------------------------
    doc_urls: list[str] = field(default_factory=list)
    local_pdfs: list[str] = field(default_factory=list)

    # --- our classification (clearly derived, never presented as source) --
    is_smd: bool = False
    smd_score: int = 0
    matched_terms: list[str] = field(default_factory=list)
    tags: list[str] = field(default_factory=list)

    # --- bookkeeping ----------------------------------------------------
    first_seen: str = ""
    last_seen: str = ""
    raw_row: Optional[str] = None    # verbatim source text, for audit
    detail: dict = field(default_factory=dict)   # detail-page fields (office, contact, procedure)
    scan: dict = field(default_factory=dict)     # terms read out of the tender PDFs

    @property
    def uid(self) -> str:
        """Stable id. Prefer the source's own reference; fall back to a hash
        of source+title+closing so the same advert never double-notifies."""
        basis = self.tender_no or f"{self.title}|{self.closing}|{self.organisation}"
        return hashlib.sha1(f"{self.source}|{basis}".encode("utf-8", "replace")).hexdigest()[:16]

    def to_dict(self) -> dict:
        d = asdict(self)
        d["uid"] = self.uid
        return d


def clean(s: Optional[str]) -> Optional[str]:
    """Collapse whitespace. Returns None for empty — never an empty string,
    so the app can reliably distinguish 'not stated' from ''."""
    if s is None:
        return None
    s = re.sub(r"\s+", " ", str(s)).strip()
    s = s.strip("  \t\r\n-–—:")
    return s or None


_DATE_PATTERNS = [
    ("%d-%m-%Y", r"\d{1,2}-\d{1,2}-\d{4}"),
    ("%d/%m/%Y", r"\d{1,2}/\d{1,2}/\d{4}"),
    ("%d.%m.%Y", r"\d{1,2}\.\d{1,2}\.\d{4}"),
    ("%Y-%m-%d", r"\d{4}-\d{1,2}-\d{1,2}"),
    ("%d-%b-%Y", r"\d{1,2}-[A-Za-z]{3}-\d{4}"),
    ("%d %b %Y", r"\d{1,2} [A-Za-z]{3} \d{4}"),
    ("%d %B %Y", r"\d{1,2} [A-Za-z]{4,9} \d{4}"),
    ("%b %d, %Y", r"[A-Za-z]{3} \d{1,2}, \d{4}"),
    ("%B %d, %Y", r"[A-Za-z]{4,9} \d{1,2}, \d{4}"),
]


def parse_date(s: Optional[str]) -> Optional[str]:
    """Parse a date the source printed. Returns ISO yyyy-mm-dd, or None.

    Deliberately conservative: an unrecognised format returns None rather
    than a best guess, because a wrong closing date is worse than a blank one.
    """
    s = clean(s)
    if not s:
        return None
    for fmt, pat in _DATE_PATTERNS:
        m = re.search(pat, s)
        if not m:
            continue
        try:
            return datetime.strptime(m.group(0), fmt).date().isoformat()
        except ValueError:
            continue
    return None


def days_left(iso: Optional[str]) -> Optional[int]:
    if not iso:
        return None
    try:
        return (date.fromisoformat(iso[:10]) - date.today()).days
    except ValueError:
        return None
