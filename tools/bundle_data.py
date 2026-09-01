"""Copy the current feed into the app so the APK works with no internet.

Run before building an APK. The copy is not committed - it is a build artifact,
and committing it would double the repository size for no benefit.
"""
import json
import pathlib
import shutil
import sys

root = pathlib.Path(__file__).resolve().parents[1]
src = root / "data" / "tenders.json"
dst = root / "app" / "src" / "main" / "assets" / "tenders.json"

if not src.exists():
    sys.exit("No data/tenders.json - run scraper/run.py first")

dst.parent.mkdir(parents=True, exist_ok=True)
feed = json.loads(src.read_text(encoding="utf-8"))
# Written minified: this ships inside the APK, so size matters.
dst.write_text(json.dumps(feed, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"bundled {feed['count']} tenders -> {dst.relative_to(root)}")

# News and market data ride along in the APK for the same reason as tenders:
# the app must be useful the moment it is installed, before any download.
for extra in ("news.json", "awards.json"):
    src_extra = root / "data" / extra
    if src_extra.exists():
        data = json.loads(src_extra.read_text(encoding="utf-8"))
        (dst.parent / extra).write_text(
            json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
        )
        print(f"bundled {extra}")
    else:
        print(f"skipped {extra} (not collected yet)")
