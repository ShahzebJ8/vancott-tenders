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
