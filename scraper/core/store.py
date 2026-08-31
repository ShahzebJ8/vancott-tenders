"""Persistent tender database (plain JSON, committed to git).

Git gives us free history: every scrape is a commit, so you can always see when
a tender appeared, changed, or vanished. No database server to pay for or babysit.

Keeps EVERY tender, not just SMD ones — the app is a full national directory
with free-text search. SMD is a flag on a record, never a filter on storage.
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from core.models import Tender

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data"
DB = DATA / "tenders.json"


def load() -> dict[str, dict]:
    if not DB.exists():
        return {}
    try:
        raw = json.loads(DB.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    return {t["uid"]: t for t in raw.get("tenders", [])}


def merge(existing: dict[str, dict], scraped: list[Tender]) -> tuple[dict[str, dict], list[dict]]:
    """Fold a scrape into the database. Returns (db, genuinely_new).

    A tender already known keeps its original first_seen — that is what makes
    "new since yesterday" honest, and stops re-notifying you about the same advert.
    """
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    new: list[dict] = []
    for t in scraped:
        d = t.to_dict()
        prev = existing.get(d["uid"])
        if prev:
            d["first_seen"] = prev.get("first_seen") or d["first_seen"]
            d["notified"] = prev.get("notified", False)
            d["local_pdfs"] = prev.get("local_pdfs") or d["local_pdfs"]
        else:
            d["notified"] = False
            new.append(d)
        d["last_seen"] = now
        existing[d["uid"]] = d
    return existing, new


def save(db: dict[str, dict], stats: dict) -> None:
    DATA.mkdir(parents=True, exist_ok=True)
    tenders = sorted(db.values(),
                     key=lambda t: (t.get("published") or "", t.get("last_seen") or ""),
                     reverse=True)
    payload = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "count": len(tenders),
        "smd_count": sum(1 for t in tenders if t.get("is_smd")),
        "sources": stats,
        "tenders": tenders,
    }
    DB.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
