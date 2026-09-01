"""Business and economy headlines for the app.

Sources are the publishers' own RSS feeds. That matters legally: RSS is
published for syndication, and it gives a headline, a short summary and a link.
We store exactly that and always link back to the publisher. Full articles are
NOT copied - that would be copyright infringement, and no amount of convenience
is worth building a product on that.

Each item is also tagged for whether it plausibly touches this business:
construction, energy, budget/PSDP, advertising, procurement. Those tags come
from words actually present in the headline, so a tag can be checked at a glance.
"""
from __future__ import annotations

import io
import re
import sys
from dataclasses import dataclass, asdict, field
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from bs4 import BeautifulSoup                # noqa: E402

from core.fetch import Fetcher               # noqa: E402
from core.models import clean                # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "news.json"

# Each publisher's own syndication feed. The publisher name travels with every
# story and is shown on the story, and tapping a story opens it on their site.
# Two more (Pakistan Today, The Nation) were tested and dropped: their servers
# fail the TLS handshake, so they are simply unavailable rather than skipped.
# Business and economy desks only. The general news feeds were dropped: they
# carry crime, politics and sport, none of which affects a bid.
FEEDS = [
    ("Dawn", "https://www.dawn.com/feeds/business"),
    ("Business Recorder", "https://www.brecorder.com/feeds/latest-news"),
    ("Business Recorder", "https://www.brecorder.com/feeds/markets"),
    ("The Express Tribune", "https://tribune.com.pk/feed/business"),
    ("ARY News", "https://arynews.tv/category/business/feed/"),
]

# Even a business desk runs the occasional crime or celebrity story. Anything
# matching these is dropped outright, whatever else it looks like.
EXCLUDE = [
    r"murder|killed|kill(?:ing|s)?|shot dead|dead body|corpse",
    r"robbery|robbed|dacoit|mugg(?:ed|ing)|theft|stolen|burglar",
    r"rape|assault|kidnap|abduct|harass",
    r"blast|bomb|terror|militant|attack(?:ed|er)?|firing",
    r"accident|crash|collision|injured|casualt",
    r"cricket|football|hockey|match|series|tournament|wicket|innings",
    r"film|movie|actor|actress|singer|drama|celebrit|wedding|engagement",
    r"weather|monsoon|rainfall|earthquake|flood(?:s|ing)?",
    r"horoscope|obituary|funeral",
    r"arrest(?:ed)?|court\s+(?:remand|bail)|FIR|police",
]

# Topics that actually move an outdoor-advertising and LED business.
TOPICS = {
    "Budget & spending": [
        r"\bbudget\b", r"\bPSDP\b", r"development\s+programme", r"finance\s+bill",
        r"\bIMF\b", r"fiscal", r"subsid", r"tax(?:ation)?\b", r"\bFBR\b",
    ],
    "Construction & projects": [
        r"construct", r"infrastructure", r"motorway", r"metro", r"flyover",
        r"housing\s+scheme", r"\bCPEC\b", r"airport", r"\bdam\b", r"project\s+approved",
    ],
    "Energy & power": [
        r"electricity", r"power\s+(?:sector|tariff|plant)", r"\bIPP\b", r"grid",
        r"solar", r"\bLNG\b", r"gas\s+tariff", r"load[- ]?shedding",
    ],
    # A bare "media" matched a wedding report and a North Korea story, so the
    # words here have to be specific to the advertising trade itself.
    "Advertising & media": [
        r"advertis(?:ing|ement)", r"billboard", r"hoarding", r"signage",
        r"out[- ]of[- ]home", r"\bOOH\b", r"ad\s+spend", r"marketing\s+spend",
        r"media\s+(?:buying|spend|industry|agency)",
    ],
    "Procurement": [
        r"\bPPRA\b", r"tender", r"procure", r"contract\s+award", r"bidding",
    ],
    "Economy": [
        r"inflation", r"interest\s+rate", r"policy\s+rate", r"\bSBP\b", r"rupee",
        r"\bdollar\b", r"exports?", r"imports?", r"\bGDP\b", r"remittance",
        r"stock\s+(?:market|exchange)", r"\bKSE[- ]?100\b",
    ],
}


# Words that make a story about Pakistan's own economy. Foreign market news is
# kept rather than dropped - the oil price and the Fed do reach Pakistan - but
# it is marked, so domestic decisions can lead.
_DOMESTIC = re.compile(
    r"pakistan|pakistani|PKR|rupee|FBR|SBP|state bank|"
    r"KSE[- ]?100|PSX|karachi|lahore|islamabad|punjab|sindh|balochistan|"
    r"khyber|CPEC|NEPRA|OGRA|PPRA|SNGPL|SSGC|"
    r"WAPDA|PSDP|IMF.{0,40}pakistan|federal (?:budget|cabinet)|"
    r"ECC|CCI|gwadar|LNG.{0,30}pakistan",
    re.I,
)


@dataclass
class Story:
    title: str
    url: str
    source: str
    published: str | None
    summary: str | None
    image: str | None = None
    topics: list[str] = field(default_factory=list)
    relevant: bool = False
    domestic: bool = False

    def to_dict(self) -> dict:
        return asdict(self)


def _topics_for(text: str) -> list[str]:
    found = []
    for name, patterns in TOPICS.items():
        if any(re.search(p, text, re.I) for p in patterns):
            found.append(name)
    return found


def _parse_date(raw: str | None) -> str | None:
    if not raw:
        return None
    try:
        return parsedate_to_datetime(raw).astimezone(timezone.utc).isoformat(timespec="seconds")
    except (TypeError, ValueError):
        return None


def _strip_html(raw: str | None) -> str | None:
    if not raw:
        return None
    text = BeautifulSoup(raw, "lxml").get_text(" ", strip=True)
    text = clean(text)
    if not text:
        return None
    # A short extract only. The link is what sends the reader to the publisher.
    return text[:320] + ("…" if len(text) > 320 else "")


def _image_from(item) -> str | None:
    for tag in ("media:content", "media:thumbnail", "enclosure"):
        el = item.find(tag)
        if el and el.get("url"):
            return el["url"]
    desc = item.find("description")
    if desc and desc.text:
        img = BeautifulSoup(desc.text, "lxml").find("img")
        if img and img.get("src"):
            return img["src"]
    return None


def fetch_feed(f: Fetcher, source: str, url: str) -> list[Story]:
    soup = BeautifulSoup(f.get(url).text, "xml")
    stories: list[Story] = []
    for item in soup.find_all("item"):
        title = clean(item.title.text if item.title else None)
        link = clean(item.link.text if item.link else None)
        if not title or not link:
            continue
        summary = _strip_html(item.description.text if item.description else None)
        topics = _topics_for(f"{title} {summary or ''}")
        # Only economic and business material is kept. A story that matches no
        # money topic, or that matches an excluded subject, is not carried at
        # all - the point of this section is decisions that move money, not a
        # general news reader.
        blob = f"{title} {summary or ''}"
        if any(re.search(p, blob, re.I) for p in EXCLUDE):
            continue
        if not topics:
            continue

        stories.append(
            Story(
                title=title,
                url=link,
                source=source,
                published=_parse_date(item.pubDate.text if item.pubDate else None),
                summary=summary,
                image=_image_from(item),
                topics=topics,
                # "Relevant" means it touches money the government is about to
                # spend, or the industry itself - not just any business news.
                domestic=bool(_DOMESTIC.search(blob)),
                relevant=any(
                    t in topics
                    for t in ("Budget & spending", "Construction & projects",
                              "Procurement", "Advertising & media")
                ),
            )
        )
    return stories


def main() -> int:
    f = Fetcher(delay=0.8, legacy_tls=True)
    all_stories: list[Story] = []
    status: dict[str, str] = {}

    for source, url in FEEDS:
        try:
            got = fetch_feed(f, source, url)
            all_stories.extend(got)
            status[source] = f"ok ({len(got)})"
            print(f"[ok]    {source:22} {len(got)} stories")
        except Exception as e:                # noqa: BLE001
            status[source] = f"error: {type(e).__name__}"
            print(f"[error] {source:22} {type(e).__name__}: {e}")

    # De-duplicate: the same story often appears in more than one feed.
    # Pakistani stories lead, then most recent first.
    seen: set[str] = set()
    unique: list[Story] = []
    for s in sorted(all_stories,
                    key=lambda x: (x.domestic, x.published or ""), reverse=True):
        key = re.sub(r"\W+", "", s.title.lower())[:70]
        if key in seen:
            continue
        seen.add(key)
        unique.append(s)

    payload = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "count": len(unique),
        "relevant_count": sum(1 for s in unique if s.relevant),
        "domestic_count": sum(1 for s in unique if s.domestic),
        "sources": status,
        "stories": [s.to_dict() for s in unique],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        __import__("json").dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    print(f"\n{len(unique)} stories, {payload['relevant_count']} relevant -> {OUT.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
