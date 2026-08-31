"""SMD / LED relevance classification.

Word-boundary matching, NOT substring. The reason is concrete: PPRA's own
keyword search returns "SCADA Enab*led* ..." for the query "LED". Substring
matching floods you with false hits and buries the real ones.

Scoring is transparent — every tender records which terms matched, so the app
shows *why* something was flagged and nothing looks like a black box.
"""
from __future__ import annotations

import re
from typing import Iterable

# Strong: essentially always a real SMD/LED-screen opportunity.
STRONG = {
    "smd screen": 10, "smd display": 10, "smd led": 10, "smd wall": 10,
    "led screen": 10, "led display": 10, "led wall": 10, "led video wall": 12,
    "video wall": 9, "digital signage": 10, "digital billboard": 10,
    "outdoor led": 10, "indoor led": 10, "led panel": 8, "led module": 8,
    "p2.5": 9, "p3": 7, "p3.91": 9, "p4": 7, "p5": 7, "p6": 7, "p8": 7, "p10": 7,
    "cob display": 10, "cob led": 10, "gob led": 10,
    "led vision": 9, "jumbotron": 10, "led ticker": 8, "moving message": 7,
    "variable message sign": 9, "vms board": 8, "electronic display": 8,
    "display board": 6, "led sign": 8, "scrolling display": 7,
}

# Supporting: only meaningful alongside a display context.
SUPPORT = {
    "led": 3, "display": 2, "screen": 2, "signage": 4, "billboard": 4,
    "hoarding": 3, "advertisement panel": 4, "outdoor advertising": 5,
    "media facade": 5, "pixel pitch": 8, "nits": 4, "sending card": 6,
    "receiving card": 6, "novastar": 8, "processor": 1, "controller": 1,
}

# Kill terms: these make an "LED" hit almost certainly irrelevant.
NEGATIVE = {
    "led bulb", "led light", "led lights", "led lighting", "led tube",
    "led street light", "streetlight", "led lamp", "led luminaire",
    "solar led", "led torch", "flood light", "floodlight", "led fitting",
    "led downlight", "led batten", "surgical light", "operation theatre light",
}

THRESHOLD = 7  # tuned so "LED street lights" scores below, "LED screen" above


def _terms(text: str, vocab: Iterable[str]) -> list[tuple[str, int]]:
    hits = []
    for term in vocab:
        # \b won't work around '.' in p3.91, so build a tolerant boundary.
        pat = r"(?<![a-z0-9])" + re.escape(term).replace(r"\ ", r"[\s\-_]+") + r"(?![a-z0-9])"
        if re.search(pat, text):
            hits.append(term)
    return hits


def classify(*parts: str | None) -> tuple[bool, int, list[str]]:
    """Return (is_smd, score, matched_terms) for the given text fragments."""
    text = " ".join(p for p in parts if p).lower()
    text = re.sub(r"\s+", " ", text)
    if not text:
        return False, 0, []

    neg = _terms(text, NEGATIVE)
    strong = _terms(text, STRONG)
    support = _terms(text, SUPPORT)

    score = sum(STRONG[t] for t in strong) + sum(SUPPORT[t] for t in support)
    # A lighting tender that also says "LED" should not survive on the
    # supporting words alone. Strong display terms still win.
    if neg and not strong:
        score -= 12 * len(neg)

    matched = strong + support
    return score >= THRESHOLD, max(score, 0), matched


PROVINCE_CITIES = {
    "Punjab": ["lahore", "rawalpindi", "faisalabad", "multan", "gujranwala", "sialkot",
               "bahawalpur", "sargodha", "sahiwal", "sheikhupura", "jhelum", "okara",
               "dera ghazi khan", "rahim yar khan", "gujrat", "kasur", "chiniot", "vehari"],
    "Sindh": ["karachi", "hyderabad", "sukkur", "larkana", "nawabshah", "mirpurkhas",
              "shikarpur", "jacobabad", "thatta", "badin", "khairpur"],
    "Khyber Pakhtunkhwa": ["peshawar", "mardan", "abbottabad", "swat", "mingora", "kohat",
                           "bannu", "dera ismail khan", "nowshera", "charsadda", "swabi", "chitral"],
    "Balochistan": ["quetta", "gwadar", "turbat", "khuzdar", "sibi", "chaman", "zhob", "hub"],
    "Islamabad": ["islamabad"],
    "Azad Kashmir": ["muzaffarabad", "mirpur", "kotli", "rawalakot", "bhimber"],
    "Gilgit-Baltistan": ["gilgit", "skardu", "hunza", "chilas"],
}


def locate(*parts: str | None) -> tuple[str | None, str | None]:
    """Best-effort (city, province) from text the source actually printed.

    Returns (None, None) when nothing matches — never a default like 'Lahore'.
    """
    text = " ".join(p for p in parts if p).lower()
    for province, cities in PROVINCE_CITIES.items():
        for city in cities:
            if re.search(r"(?<![a-z])" + re.escape(city) + r"(?![a-z])", text):
                return city.title(), province
    return None, None
