"""Tender Scanner — point it at any tender PDF and get the terms out.

Standalone: works on any PDF on the machine, whether or not it came from the
scraper. Reads the key terms, pulls out technical specification tables, and
writes a report you can read or hand to someone.

    python tenderscan/scan.py "C:/path/to/tender.pdf"
    python tenderscan/scan.py "C:/folder/of/pdfs"          (whole folder = one tender)
    python tenderscan/scan.py file.pdf --html report.html  (also write a report)
    python tenderscan/scan.py file.pdf --json out.json     (machine-readable)

Every term shown carries the page it came from and the exact line, so anything
the scanner gets wrong is visible at a glance rather than quietly believed.
"""
from __future__ import annotations

import argparse
import html
import io
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scraper"))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from core.pdfextract import Extraction, extract_all   # noqa: E402

CONF_LABEL = {
    "high": "certain",
    "medium": "likely",
    "low": "check this",
}


def collect(target: Path) -> dict[str, str]:
    """Every PDF to treat as one tender."""
    if target.is_dir():
        return {p.name: str(p) for p in sorted(target.glob("*.pdf"))}
    return {target.name: str(target)}


def print_report(r: Extraction) -> None:
    print()
    print("=" * 72)
    print(r.summary())
    print("=" * 72)

    if r.facts:
        print("\nKEY TERMS\n")
        for f in r.facts:
            head = f"  {f.label}"
            if f.value:
                head += f": {f.value}"
            print(head)
            if f.plain:
                print(f"      {f.plain}")
            ev = f.evidence
            where = f"page {ev.page}"
            if ev.clause_no:
                where += f", clause {ev.clause_no}"
            print(f"      [{CONF_LABEL.get(f.confidence, f.confidence)}] {where} — {ev.method}")
            print(f"      \"{ev.quote[:200]}\"")
            print()

    if r.specs:
        print(f"TECHNICAL SPECIFICATION ({len(r.specs)} lines)\n")
        for s in r.specs:
            print(f"  {s.name[:46]:48} {s.value[:44]}")
        print()

    if r.unreadable:
        print("COULD NOT READ\n")
        for u in r.unreadable:
            print(f"  {u}")
        print()


def write_html(r: Extraction, path: Path, title: str) -> None:
    """A self-contained report page, watermarked, safe to send to someone."""
    def esc(x: str) -> str:
        return html.escape(x or "")

    facts = "".join(
        f"""
        <div class="fact">
          <div class="fact-head">
            <span class="label">{esc(f.label)}</span>
            {f'<span class="value">{esc(f.value)}</span>' if f.value else ''}
            <span class="conf conf-{f.confidence}">{CONF_LABEL.get(f.confidence, '')}</span>
          </div>
          <p class="plain">{esc(f.plain)}</p>
          <p class="where">page {f.evidence.page}
            {f', clause {esc(f.evidence.clause_no)}' if f.evidence.clause_no else ''}
            — {esc(f.evidence.method)} — {esc(f.evidence.document)}</p>
          <blockquote>{esc(f.evidence.quote[:400])}</blockquote>
        </div>"""
        for f in r.facts
    )

    specs = "".join(
        f"<tr><td>{esc(s.name)}</td><td>{esc(s.value)}</td><td>p{s.page}</td></tr>"
        for s in r.specs
    )
    specs_block = (
        f"<h2>Technical specification</h2><table>"
        f"<tr><th>Item</th><th>Required</th><th></th></tr>{specs}</table>"
        if specs else ""
    )

    warn = (
        "<h2>Could not read</h2><ul>"
        + "".join(f"<li>{esc(u)}</li>" for u in r.unreadable)
        + "</ul>"
        if r.unreadable else ""
    )

    page = f"""<!doctype html><html><head><meta charset="utf-8">
<title>{esc(title)} — Tender Desk</title>
<style>
  :root {{ --ink:#12151c; --muted:#5f6b7e; --line:#e3e7ee; --brand:#2769B3; }}
  * {{ box-sizing:border-box; }}
  body {{ font-family:-apple-system,Segoe UI,Roboto,sans-serif; color:var(--ink);
         margin:0; padding:40px 28px 80px; max-width:900px; margin-inline:auto;
         position:relative; background:#fff; }}
  /* Watermark: large, faint, behind everything, and it cannot be selected or
     copied out of the page. */
  body::before {{ content:"TENDER DESK"; position:fixed; inset:0;
    display:flex; align-items:center; justify-content:center;
    font-size:clamp(60px,14vw,180px); font-weight:800; letter-spacing:.06em;
    color:rgba(39,105,179,.06); transform:rotate(-28deg);
    pointer-events:none; user-select:none; z-index:0; white-space:nowrap; }}
  main {{ position:relative; z-index:1; }}
  h1 {{ font-size:24px; margin:0 0 4px; }}
  h2 {{ font-size:15px; text-transform:uppercase; letter-spacing:.08em;
        color:var(--brand); margin:34px 0 10px; }}
  .sub {{ color:var(--muted); font-size:14px; margin:0 0 8px; }}
  .fact {{ border:1px solid var(--line); border-radius:10px; padding:14px 16px;
           margin:10px 0; }}
  .fact-head {{ display:flex; gap:10px; align-items:baseline; flex-wrap:wrap; }}
  .label {{ font-weight:600; }}
  .value {{ font-weight:700; color:var(--brand); }}
  .conf {{ font-size:11px; text-transform:uppercase; letter-spacing:.06em;
           padding:2px 7px; border-radius:4px; }}
  .conf-high {{ background:#e6f4ec; color:#1c7a4a; }}
  .conf-medium {{ background:#eef2fa; color:#2f5ea8; }}
  .conf-low {{ background:#fdf0e6; color:#a35a12; }}
  .plain {{ margin:8px 0 6px; }}
  .where {{ font-size:12px; color:var(--muted); margin:0 0 8px; }}
  blockquote {{ margin:0; padding:8px 12px; border-left:3px solid var(--line);
                color:var(--muted); font-size:13px; }}
  table {{ border-collapse:collapse; width:100%; font-size:14px; }}
  td,th {{ border-bottom:1px solid var(--line); padding:7px 8px; text-align:left; }}
  th {{ color:var(--muted); font-weight:600; font-size:12px; text-transform:uppercase; }}
  footer {{ margin-top:40px; color:var(--muted); font-size:12px;
            border-top:1px solid var(--line); padding-top:14px; }}
</style></head><body><main>
<h1>{esc(title)}</h1>
<p class="sub">{esc(r.summary())}</p>
<p class="sub">Read from: {esc(", ".join(r.documents))}</p>
<h2>Key terms</h2>
{facts or "<p>No standard terms recognised.</p>"}
{specs_block}
{warn}
<footer>
  Every line above is taken from the tender documents themselves, with the page
  it came from. Nothing here is a substitute for reading the original documents
  before you bid.
</footer>
</main></body></html>"""
    path.write_text(page, encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description="Read the terms out of a tender PDF.")
    ap.add_argument("target", help="a PDF file, or a folder of PDFs for one tender")
    ap.add_argument("--html", help="also write an HTML report to this path")
    ap.add_argument("--json", dest="json_path", help="also write JSON to this path")
    ap.add_argument("--quiet", action="store_true", help="do not print the report")
    args = ap.parse_args()

    target = Path(args.target)
    if not target.exists():
        print(f"Not found: {target}")
        return 1

    pdfs = collect(target)
    if not pdfs:
        print(f"No PDFs in {target}")
        return 1

    print(f"Reading {len(pdfs)} document(s)...")
    result = extract_all(pdfs)

    if not args.quiet:
        print_report(result)

    title = target.stem if target.is_file() else target.name
    if args.html:
        out = Path(args.html)
        write_html(result, out, title)
        print(f"Report written to {out}")
    if args.json_path:
        Path(args.json_path).write_text(
            json.dumps(result.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"JSON written to {args.json_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
