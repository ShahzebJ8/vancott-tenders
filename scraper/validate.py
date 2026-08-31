"""Check that data/tenders.json matches what the Android app expects.

Worth having as its own step: the app and the scraper are written in different
languages, so a renamed field would otherwise only show up as a blank screen on
someone's phone. This fails loudly instead.
"""
from __future__ import annotations

import io
import json
import sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# Field name -> whether the app requires it to be non-null on every record.
REQUIRED = {
    "uid": True, "source": True, "source_name": True, "url": True, "title": True,
    "organisation": False, "tender_no": False, "city": False, "province": False,
    "published": False, "closing": False, "value": False, "category": False,
    "description": False, "doc_urls": True, "is_smd": True, "smd_score": True,
    "matched_terms": True, "tags": True, "first_seen": True, "detail": True,
}


def main() -> int:
    path = Path(__file__).resolve().parents[1] / "data" / "tenders.json"
    if not path.exists():
        print("FAIL: no data/tenders.json - run scraper/run.py first")
        return 1

    feed = json.loads(path.read_text(encoding="utf-8"))
    problems: list[str] = []

    for key in ("generated", "count", "smd_count", "sources", "tenders"):
        if key not in feed:
            problems.append(f"feed is missing top-level '{key}'")

    tenders = feed.get("tenders", [])
    if feed.get("count") != len(tenders):
        problems.append(f"count says {feed.get('count')} but there are {len(tenders)} tenders")

    uids: set[str] = set()
    for i, t in enumerate(tenders):
        for field, required in REQUIRED.items():
            if field not in t:
                problems.append(f"tender[{i}] missing field '{field}'")
            elif required and t[field] is None:
                problems.append(f"tender[{i}] has null '{field}' but the app requires a value")
        uid = t.get("uid")
        if uid in uids:
            problems.append(f"duplicate uid {uid} - the app uses uid as a list key and will crash")
        uids.add(uid)

    # Dates must be ISO, because the app parses them with LocalDate.parse.
    bad_dates = [t["tender_no"] or t["uid"] for t in tenders
                 if t.get("closing") and not _iso(t["closing"])]
    if bad_dates:
        problems.append(f"{len(bad_dates)} non-ISO closing dates, e.g. {bad_dates[:3]}")

    smd = [t for t in tenders if t.get("is_smd")]
    print(f"tenders      {len(tenders)}")
    print(f"smd flagged  {len(smd)}")
    src = ", ".join(f"{k}={v.get('status')}" for k, v in feed.get("sources", {}).items())
    print(f"sources      {src}")
    print(f"with closing {sum(1 for t in tenders if t.get('closing'))}")
    print(f"with docs    {sum(1 for t in tenders if t.get('doc_urls'))}")

    if problems:
        print(f"\nFAIL - {len(problems)} problem(s):")
        for p in problems[:25]:
            print("  -", p)
        return 1
    print("\nOK - feed matches the app's data model")
    return 0


def _iso(s: str) -> bool:
    try:
        from datetime import date
        date.fromisoformat(s[:10])
        return True
    except ValueError:
        return False


if __name__ == "__main__":
    raise SystemExit(main())
