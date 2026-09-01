"""Read a tender's PDFs and pull out the clauses that decide whether you can bid.

RULE FOR THIS FILE: nothing is ever summarised from imagination. Every plain
language line is built from text actually found in the document, and the exact
sentence it came from is kept alongside it so a human can check it in one look.

That rule is not decoration. These are government and military procurements.
A confidently worded but invented "simple explanation" of an eligibility or
bid-security clause could lose a bid, or worse. Where a document does not state
something, the answer is "not stated in the documents" - never a guess.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, asdict
from typing import Iterable

try:
    from pypdf import PdfReader
except ImportError:                        # scraper can still run without PDFs
    PdfReader = None                       # type: ignore


MAX_PAGES = 40          # beyond this a document is a spec book, not a notice
MAX_CHARS = 400_000


@dataclass
class Clause:
    """One extracted point.

    plain  - what it means, in ordinary words, built from real values
    quote  - the sentence in the document it came from, verbatim
    source - which file it came from
    """
    key: str
    label: str
    plain: str
    quote: str
    source: str

    def to_dict(self) -> dict:
        return asdict(self)


def read_pdf_text(path: str) -> str:
    """Plain text of a PDF. Returns '' for scanned or unreadable files.

    A scanned tender notice is an image, not text - we detect that and say so,
    rather than reporting "nothing found" as though the clauses were absent.
    """
    if PdfReader is None:
        return ""
    try:
        reader = PdfReader(path)
    except Exception:                       # noqa: BLE001
        return ""
    out: list[str] = []
    for page in reader.pages[:MAX_PAGES]:
        try:
            out.append(page.extract_text() or "")
        except Exception:                   # noqa: BLE001
            continue
        if sum(len(x) for x in out) > MAX_CHARS:
            break
    return _tidy(" ".join(out))


def _tidy(text: str) -> str:
    text = text.replace(" ", " ")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\s*\n\s*", " ", text)
    return re.sub(r"\s{2,}", " ", text).strip()


def _sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[.;:])\s+(?=[A-Z0-9])", text)
    return [p.strip() for p in parts if 25 < len(p.strip()) < 600]


def _find(sentences: Iterable[str], patterns: list[str]) -> str | None:
    """First sentence matching any pattern, returned verbatim."""
    for s in sentences:
        for p in patterns:
            if re.search(p, s, re.I):
                return s
    return None


# Money written the way Pakistani tender documents write it.
_MONEY = r"(?:Rs\.?|PKR|Rupees)\s*[\d,]+(?:\.\d+)?(?:\s*(?:million|lac|lakh|crore))?"
_PERCENT = r"\d+(?:\.\d+)?\s*%"


def extract(pdf_paths: dict[str, str]) -> tuple[list[Clause], list[str]]:
    """Read every document for one tender.

    pdf_paths: {filename: local path}
    Returns (clauses, warnings). Warnings name documents we could not read, so
    a scanned file is visible rather than silently ignored.
    """
    clauses: list[Clause] = []
    warnings: list[str] = []
    seen_keys: set[str] = set()

    for name, path in pdf_paths.items():
        text = read_pdf_text(path)
        if len(text) < 200:
            warnings.append(
                f"{name}: could not read text (it is most likely a scanned image, "
                f"so its clauses are not included below - open the file itself)"
            )
            continue

        for c in _extract_one(text, name):
            if c.key in seen_keys:
                continue            # first document wins; usually the notice
            seen_keys.add(c.key)
            clauses.append(c)

    return clauses, warnings


def _extract_one(text: str, source: str) -> list[Clause]:
    ss = _sentences(text)
    found: list[Clause] = []

    def add(key: str, label: str, plain: str, quote: str | None) -> None:
        if quote:
            found.append(Clause(key, label, plain, quote.strip(), source))

    # --- bid security -----------------------------------------------------
    q = _find(ss, [r"bid\s*security", r"earnest\s*money", r"bid\s*securing\s*declaration"])
    if q:
        amount = re.search(_MONEY, q)
        pct = re.search(_PERCENT, q)
        form = []
        for f, word in [("pay order", r"pay\s*order"), ("demand draft", r"demand\s*draft"),
                        ("bank guarantee", r"bank\s*guarantee"), ("CDR", r"\bCDR\b"),
                        ("bid securing declaration", r"bid\s*securing\s*declaration")]:
            if re.search(word, q, re.I):
                form.append(f)

        if amount:
            plain = f"You must attach money as a deposit with your bid: {amount.group(0)}."
        elif pct:
            plain = f"You must attach a deposit with your bid worth {pct.group(0)} of your bid price."
        else:
            plain = ("You must attach a bid deposit. The document refers to a separate "
                     "section for the amount, so check the bidding document for the figure.")
        if form:
            plain += " It has to be in the form of " + " or ".join(form) + "."
        plain += " You get this money back if you do not win."
        add("bid_security", "Bid deposit (bid security)", plain, q)

    # --- cost of the bidding document ------------------------------------
    q = _find(ss, [r"(?:tender|bidding)\s*(?:document|fee).{0,60}(?:fee|price|cost|non-?refundable)",
                   r"non-?refundable.{0,40}(?:fee|amount)"])
    if q:
        amount = re.search(_MONEY, q)
        plain = (f"To get the bidding papers you pay {amount.group(0)}."
                 if amount else "There is a fee to obtain the bidding papers.")
        plain += " This one is not refundable."
        add("document_fee", "Cost of the bidding papers", plain, q)

    # --- who is allowed to bid -------------------------------------------
    # "eligible" alone is far too loose: a tender notice says it will "cover
    # eligible payments under the contract", which has nothing to do with who
    # may bid. The word must sit next to a bidder, firm or requirement.
    q = _find(ss, [r"eligib(?:le|ility)\s*(?:bidders?|firms?|contractors?|suppliers?)",
                   r"(?:bidders?|firms?)\s*(?:must|shall|should)\s*(?:be|have|possess)",
                   r"qualified\s*(?:bidders|firms)",
                   r"registered\s*with.{0,40}(?:FBR|SECP|PEC|income\s*tax|sales\s*tax)"])
    if q and not re.search(r"eligible\s*payments?", q, re.I):
        need = []
        for label, pat in [("an active tax number (NTN)", r"\bNTN\b|national\s*tax"),
                           ("sales tax registration (GST)", r"\bGST\b|sales\s*tax"),
                           ("PEC registration", r"\bPEC\b"),
                           ("SECP registration", r"\bSECP\b"),
                           ("registration with FBR's active taxpayer list", r"active\s*tax\s*payer|\bATL\b"),
                           ("not being blacklisted", r"black\s*list")]:
            if re.search(pat, q, re.I):
                need.append(label)
        plain = "Who is allowed to bid. "
        plain += ("You need " + ", ".join(need) + "." if need
                  else "The document sets conditions on who may bid - read the quoted line.")
        add("eligibility", "Who can bid", plain, q)

    # --- deadline and where to hand it in ---------------------------------
    q = _find(ss, [r"(?:bids?|tenders?|proposals?).{0,60}(?:received|submitted|dropped).{0,60}"
                   r"(?:on|before|till|by|not later)",
                   r"last\s*date.{0,40}submission"])
    if q:
        time = re.search(r"\d{1,2}[:.]\d{2}\s*(?:AM|PM|hours|hrs)", q, re.I)
        date = re.search(r"\d{1,2}[-/ ][A-Za-z]{3,9}[-/ ]\d{4}|[A-Za-z]{3,9} \d{1,2},? \d{4}", q)
        plain = "When and where your bid must be handed in."
        if date:
            plain += f" Date: {date.group(0)}."
        if time:
            plain += f" Time: {time.group(0)}. Arriving after this time means your bid is not accepted."
        add("submission", "Deadline for handing in your bid", plain, q)

    # --- opening ----------------------------------------------------------
    q = _find(ss, [r"(?:bids?|tenders?).{0,40}(?:will be )?opened", r"opening.{0,30}(?:date|time)"])
    if q:
        time = re.search(r"\d{1,2}[:.]\d{2}\s*(?:AM|PM|hours|hrs)", q, re.I)
        plain = ("Bids are opened in front of whoever attends"
                 + (f", at {time.group(0)}." if time else ".")
                 + " You or your representative may be present.")
        add("opening", "When bids are opened", plain, q)

    # --- how long your price must stand -----------------------------------
    q = _find(ss, [r"valid(?:ity)?\s*(?:for|of|period).{0,40}\d+\s*days", r"\d+\s*days.{0,30}valid"])
    if q:
        days = re.search(r"(\d+)\s*days", q, re.I)
        plain = (f"Your quoted price must stay valid for {days.group(1)} days."
                 if days else "Your quoted price must stay valid for a fixed period.")
        plain += " You cannot change it during that time."
        add("validity", "How long your price must hold", plain, q)

    # --- delivery / completion -------------------------------------------
    # Only accept this clause when an actual period is stated. Without that it
    # matched page footers and told the reader nothing.
    q = _find(ss, [r"(?:delivery|completion)\s*(?:period|time|schedule)\D{0,30}\d+\s*(?:days|weeks|months)",
                   r"within\s*\d+\s*(?:days|weeks|months)\D{0,50}(?:deliver|complete|install|supply)",
                   r"(?:deliver|complete|install|supply)\D{0,50}within\s*\d+\s*(?:days|weeks|months)"])
    if q and not _is_boilerplate(q):
        per = re.search(r"(\d+)\s*(days|weeks|months)", q, re.I)
        if per:
            add("delivery", "How long you get to deliver",
                f"You must finish the work or deliver within {per.group(1)} "
                f"{per.group(2).lower()} of getting the order.", q)

    # --- penalties --------------------------------------------------------
    q = _find(ss, [r"liquidated\s*damages", r"penalty.{0,40}(?:delay|late)",
                   r"delay.{0,30}(?:penalty|deduct)"])
    if q:
        pct = re.search(_PERCENT, q)
        plain = ("If you are late, money is cut from your payment"
                 + (f" - {pct.group(0)} as stated." if pct else " as a penalty.")
                 + " Do not promise a date you cannot meet.")
        add("penalty", "Penalty for being late", plain, q)

    # --- performance guarantee -------------------------------------------
    q = _find(ss, [r"performance\s*(?:guarantee|security|bond)"])
    if q:
        pct = re.search(_PERCENT, q)
        amount = re.search(_MONEY, q)
        val = pct.group(0) if pct else (amount.group(0) if amount else None)
        plain = ("If you win, you must deposit a further guarantee"
                 + (f" of {val}." if val else ".")
                 + " It is held until the job is finished properly.")
        add("performance", "Guarantee if you win", plain, q)

    # --- paperwork to attach ----------------------------------------------
    q = _find(ss, [r"(?:attach|enclose|accompan|submit).{0,60}"
                   r"(?:affidavit|undertaking|certificate|documents|copies)"])
    if q:
        docs = []
        for label, pat in [("an affidavit", r"affidavit"), ("an undertaking", r"undertaking"),
                           ("company registration", r"registration\s*certificate|incorporat"),
                           ("tax certificates", r"\bNTN\b|\bGST\b|tax\s*certificate"),
                           ("past experience certificates", r"experience|completion\s*certificate"),
                           ("audited accounts", r"audit|financial\s*statement"),
                           ("bank statement", r"bank\s*statement")]:
            if re.search(pat, q, re.I):
                docs.append(label)
        plain = ("Papers you must put in the envelope: " + ", ".join(docs) + "."
                 if docs else "The document lists papers you must attach - read the quoted line.")
        plain += " A missing paper is the most common reason bids are thrown out."
        add("paperwork", "Papers you must attach", plain, q)

    # --- two-envelope procedure -------------------------------------------
    q = _find(ss, [r"single\s*stage[- ]two\s*envelope", r"two\s*envelope", r"single\s*envelope",
                   r"two\s*stage"])
    if q:
        if re.search(r"two\s*envelope", q, re.I):
            plain = ("Two separate sealed envelopes: one with your technical details, one with "
                     "your price. The price envelope is only opened if you pass the technical "
                     "stage. Putting the price in the wrong envelope disqualifies you.")
        else:
            plain = "One sealed envelope containing everything, including your price."
        add("procedure", "How to package your bid", plain, q)

    # --- joint ventures ---------------------------------------------------
    q = _find(ss, [r"joint\s*venture", r"\bJV\b", r"consorti"])
    if q:
        allowed = not re.search(r"(?:not|no)\s*(?:be\s*)?(?:allowed|permitted|accepted)", q, re.I)
        plain = ("You may bid together with another company as a joint venture."
                 if allowed else "Joint ventures are NOT allowed - you must bid on your own.")
        add("joint_venture", "Bidding with a partner", plain, q)

    # --- right to reject ---------------------------------------------------
    q = _find(ss, [r"reject\s*(?:any|all)\s*(?:or\s*all\s*)?bids", r"right\s*to\s*reject"])
    if q:
        add("rejection", "They can cancel",
            "The department can reject any or all bids without taking on any liability. "
            "This is standard in every government tender, but it means nothing is guaranteed "
            "until a contract is signed.", q)

    return found


def summarise(clauses: list[Clause], warnings: list[str]) -> str:
    """A short heading line for the app, stating how much was actually found."""
    if not clauses and warnings:
        return ("The documents for this tender could not be read as text "
                "(most likely scanned images). Open them directly.")
    if not clauses:
        return "No standard clauses were recognised in the documents."
    return (f"{len(clauses)} key points found in the tender documents. "
            f"Each one shows the exact line it came from.")
