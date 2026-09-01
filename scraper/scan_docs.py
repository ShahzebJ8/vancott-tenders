"""Download each tender's PDFs, read the terms out of them, store the result.

Runs after the scrapers. The output goes into every tender's `scan` field, so
the app shows the breakdown without doing any PDF work on the phone - which is
the only sane place for it: a bidding document can be 75 pages, and parsing that
on an older Android would be slow and would drain the battery.

Scanning is deliberately budgeted. Tenders are scanned in order of how much they
matter (SMD matches first), with a cap per run, so a scrape never turns into an
hour of downloading. Anything not reached this run is picked up next run.
"""
from __future__ import annotations

import argparse
import io
import json
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from core import store                       # noqa: E402
from core.fetch import Fetcher               # noqa: E402
from core.pdfextract import extract_all      # noqa: E402
from core.models import Tender               # noqa: E402
from sources import epms_ppra                # noqa: E402

MAX_PDF_BYTES = 40 * 1024 * 1024


def priority(t: dict) -> tuple:
    """Most valuable first: SMD matches, then closing soonest."""
    return (
        0 if t.get("is_smd") else 1,
        t.get("closing") or "9999-99-99",
    )


def ensure_doc_urls(f: Fetcher, t: dict) -> list[str]:
    """Real PDF links for a tender.

    EPMS only shows its documents on the detail page, so that page is visited
    once here rather than for all 1,700 tenders during the main scrape.
    """
    urls = [u for u in (t.get("doc_urls") or []) if "/pdf?file=" in u or u.endswith(".pdf")]
    if urls or t.get("source") != epms_ppra.KEY:
        return urls or (t.get("doc_urls") or [])

    stub = Tender(source=t["source"], source_name=t["source_name"], url=t["url"],
                  title=t["title"])
    try:
        enriched = epms_ppra.enrich(f, stub)
    except Exception:                        # noqa: BLE001
        return []
    found = enriched.doc_urls or []
    if found:
        t["doc_urls"] = found
        # Keep the richer detail we just paid a request for.
        if enriched.detail:
            t["detail"] = {**(t.get("detail") or {}), **enriched.detail}
        if enriched.value and not t.get("value"):
            t["value"] = enriched.value
    return found


def needs_scan(t: dict) -> bool:
    if not t.get("doc_urls"):
        return False
    scan = t.get("scan") or {}
    if not scan:
        return True
    # Re-read if the document list changed since the last scan.
    return scan.get("doc_count") != len(t["doc_urls"])


def download(f: Fetcher, urls: list[str], into: Path) -> dict[str, str]:
    """Fetch a tender's documents. Anything that is not a PDF is skipped rather
    than saved under a .pdf name it does not deserve."""
    out: dict[str, str] = {}
    for i, url in enumerate(urls, start=1):
        try:
            r = f.get(url)
        except Exception:                    # noqa: BLE001
            continue
        if not r.content.startswith(b"%PDF") or len(r.content) > MAX_PDF_BYTES:
            continue
        name = f"doc_{i}.pdf"
        (into / name).write_bytes(r.content)
        out[name] = str(into / name)
    return out


def compact(result, doc_count: int) -> dict:
    """Trim the extraction to what the app actually shows.

    Quotes are capped: the evidence has to be readable on a phone, and the full
    clause text would multiply the feed size for no benefit.
    """
    d = result.to_dict()
    for fact in d["facts"]:
        fact["evidence"]["quote"] = fact["evidence"]["quote"][:400]
    return {
        "scanned": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "doc_count": doc_count,
        "summary": d["summary"],
        "facts": d["facts"],
        "specs": d["specs"][:60],
        "unreadable": d["unreadable"],
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=40, help="tenders to scan this run")
    ap.add_argument("--smd-only", action="store_true")
    args = ap.parse_args()

    db = store.load()
    if not db:
        print("No tenders. Run scraper/run.py first.")
        return 1

    todo = [t for t in db.values() if needs_scan(t)]
    if args.smd_only:
        todo = [t for t in todo if t.get("is_smd")]
    todo.sort(key=priority)
    todo = todo[: args.limit]

    if not todo:
        print("Nothing to scan.")
        return 0

    print(f"Scanning {len(todo)} tender(s)...")
    f = Fetcher(delay=0.6, legacy_tls=True)
    done = failed = 0

    for t in todo:
        workdir = Path(tempfile.mkdtemp(prefix="scan_"))
        try:
            urls = ensure_doc_urls(f, t)
            pdfs = download(f, urls, workdir) if urls else {}
            if not pdfs:
                # Record the attempt so we do not retry the same dead links
                # on every single run.
                t["scan"] = {
                    "scanned": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                    "doc_count": len(t.get("doc_urls") or []),
                    "summary": "The published documents could not be downloaded.",
                    "facts": [], "specs": [], "unreadable": [],
                }
                failed += 1
                continue

            result = extract_all(pdfs)
            t["scan"] = compact(result, len(t.get("doc_urls") or []))
            done += 1
            flag = "SMD" if t.get("is_smd") else "   "
            print(f"  [{flag}] {t['title'][:52]:54} "
                  f"{len(result.facts)} terms, {len(result.specs)} specs")
        except Exception as e:               # noqa: BLE001
            # One bad document must never stop the run.
            print(f"  [err] {t['title'][:52]:54} {type(e).__name__}")
            failed += 1
        finally:
            shutil.rmtree(workdir, ignore_errors=True)

    stats = json.loads(store.DB.read_text(encoding="utf-8")).get("sources", {})
    store.save(db, stats)
    print(f"\nscanned {done}, failed {failed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
