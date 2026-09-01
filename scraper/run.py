"""Entry point. Runs every source, merges, reports.

One source failing never kills the run — each is isolated and its status is
recorded, so the app can show "KPPRA: unreachable" instead of silently
pretending that province has no tenders.
"""
from __future__ import annotations

import argparse
import io
import sys
import traceback
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from core import store                    # noqa: E402
from core.fetch import Blocked, Fetcher   # noqa: E402
from sources import epads, epms_ppra, ppra_punjab   # noqa: E402

SOURCES = [epms_ppra, ppra_punjab, epads]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-pages", type=int, default=60)
    ap.add_argument("--only", help="run a single source by KEY")
    args = ap.parse_args()

    f = Fetcher(delay=1.0)
    f_legacy = Fetcher(delay=1.0, legacy_tls=True)   # for old-TLS provincial portals
    db = store.load()
    before = len(db)
    stats: dict[str, dict] = {}
    all_new: list[dict] = []

    for mod in SOURCES:
        if args.only and mod.KEY != args.only:
            continue
        started = datetime.now(timezone.utc)
        try:
            fetcher = f_legacy if getattr(mod, "LEGACY_TLS", False) else f
            got = mod.scrape(fetcher, max_pages=args.max_pages)
            db, new = store.merge(db, got)
            all_new += new
            stats[mod.KEY] = {"name": mod.NAME, "status": "ok",
                              "scraped": len(got), "new": len(new),
                              "checked": started.isoformat(timespec="seconds")}
            print(f"[ok]      {mod.NAME:28} {len(got):5} tenders, {len(new):4} new")
        except Blocked:
            stats[mod.KEY] = {"name": mod.NAME, "status": "blocked",
                              "note": "source demanded human verification",
                              "checked": started.isoformat(timespec="seconds")}
            print(f"[BLOCKED] {mod.NAME:28} needs a manual check")
        except Exception as e:                       # noqa: BLE001
            stats[mod.KEY] = {"name": mod.NAME, "status": "error",
                              "note": f"{type(e).__name__}: {e}"[:300],
                              "checked": started.isoformat(timespec="seconds")}
            print(f"[ERROR]   {mod.NAME:28} {type(e).__name__}: {e}")
            traceback.print_exc(limit=2)

    store.save(db, stats)
    smd_new = [t for t in all_new if t.get("is_smd")]
    print(f"\ndb: {before} -> {len(db)}   new this run: {len(all_new)}   new SMD: {len(smd_new)}")
    for t in smd_new:
        print(f"  * [{t['smd_score']}] {t['title'][:70]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
