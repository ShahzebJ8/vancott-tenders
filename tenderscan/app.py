"""Tender Scanner — the desktop app.

Double-click "Tender Scanner.bat" and it opens in your browser. Drag a tender
PDF onto the page and it reads the terms out of it.

It runs entirely on this machine: a small local server on 127.0.0.1 that nothing
outside the computer can reach. No file ever leaves the PC, and nothing is
uploaded anywhere. That matters because these are government and military
tender documents.
"""
from __future__ import annotations

import html
import io
import json
import os
import socket
import sys
import tempfile
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scraper"))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from core.pdfextract import extract_all      # noqa: E402
from scan import write_html                  # noqa: E402

HOST = "127.0.0.1"          # local only - never reachable from the network
MAX_UPLOAD = 80 * 1024 * 1024

CONF_LABEL = {"high": "certain", "medium": "likely", "low": "check this"}


PAGE = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tender Scanner</title>
<style>
  @font-face {{ font-family:Celias; src:url('/font/regular') format('opentype'); font-weight:400; }}
  @font-face {{ font-family:Celias; src:url('/font/light') format('opentype'); font-weight:300; }}
  :root {{ --bg:#0A0D14; --panel:#121722; --raised:#1B2230; --line:#232B3A;
           --brand:#2769B3; --lit:#6BA8E0; --ink:#F0F3F8; --muted:#98A2B3;
           --faint:#5F6B7E; --ok:#3FBFA0; --warn:#E0964B; }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--ink);
          font-family:Celias,-apple-system,Segoe UI,sans-serif; font-weight:300;
          min-height:100vh; position:relative; }}
  body::before {{ content:"TENDER DESK"; position:fixed; inset:0; display:flex;
    align-items:center; justify-content:center; font-size:clamp(70px,15vw,220px);
    font-weight:400; letter-spacing:.06em; color:rgba(107,168,224,.035);
    transform:rotate(-28deg); pointer-events:none; user-select:none;
    z-index:0; white-space:nowrap; }}
  main {{ position:relative; z-index:1; max-width:980px; margin:0 auto; padding:40px 24px 90px; }}
  header {{ display:flex; align-items:baseline; gap:14px; margin-bottom:6px; }}
  h1 {{ font-size:26px; font-weight:400; margin:0; letter-spacing:-.01em; }}
  .tag {{ font-size:12px; color:var(--lit); border:1px solid var(--line);
          padding:3px 9px; border-radius:5px; }}
  .sub {{ color:var(--muted); font-size:14px; margin:0 0 26px; }}
  #drop {{ border:1.5px dashed var(--line); border-radius:14px; padding:52px 24px;
           text-align:center; background:var(--panel); transition:border-color .15s,background .15s;
           cursor:pointer; }}
  #drop.over {{ border-color:var(--brand); background:var(--raised); }}
  #drop h2 {{ margin:0 0 6px; font-size:18px; font-weight:400; }}
  #drop p {{ margin:0; color:var(--faint); font-size:14px; }}
  #files {{ display:none; }}
  .btn {{ display:inline-block; margin-top:16px; background:var(--brand); color:#fff;
          border:0; border-radius:8px; padding:11px 20px; font:inherit; font-size:14px;
          cursor:pointer; }}
  .btn.ghost {{ background:var(--raised); }}
  #status {{ margin:22px 0; color:var(--muted); font-size:14px; min-height:20px; }}
  h2.sec {{ font-size:12px; text-transform:uppercase; letter-spacing:.1em;
            color:var(--lit); margin:34px 0 10px; font-weight:400; }}
  .fact {{ border:1px solid var(--line); border-radius:10px; padding:14px 16px;
           margin:9px 0; background:var(--panel); }}
  .fh {{ display:flex; gap:10px; align-items:baseline; flex-wrap:wrap; }}
  .lab {{ font-weight:400; }}
  .val {{ color:var(--lit); font-weight:400; }}
  .conf {{ font-size:10px; text-transform:uppercase; letter-spacing:.08em;
           padding:2px 7px; border-radius:4px; }}
  .c-high {{ background:rgba(63,191,160,.16); color:var(--ok); }}
  .c-medium {{ background:rgba(107,168,224,.16); color:var(--lit); }}
  .c-low {{ background:rgba(224,150,75,.16); color:var(--warn); }}
  .plain {{ margin:9px 0 6px; color:var(--ink); font-size:15px; }}
  .where {{ font-size:12px; color:var(--faint); margin:0 0 8px; }}
  blockquote {{ margin:0; padding:8px 12px; border-left:2px solid var(--line);
                color:var(--muted); font-size:13px; }}
  table {{ border-collapse:collapse; width:100%; font-size:14px; }}
  td,th {{ border-bottom:1px solid var(--line); padding:8px; text-align:left; }}
  th {{ color:var(--faint); font-size:11px; text-transform:uppercase; letter-spacing:.07em; }}
  .note {{ color:var(--warn); font-size:14px; }}
  footer {{ margin-top:44px; padding-top:16px; border-top:1px solid var(--line);
            color:var(--faint); font-size:12px; }}
</style></head><body><main>
<header><h1>Tender Scanner</h1><span class="tag">runs on this PC only</span></header>
<p class="sub">Drag a tender PDF in. It reads the terms, the deadlines and the specification out of it.</p>

<div id="drop">
  <h2>Drop a tender PDF here</h2>
  <p>or click to choose files — several files are read as one tender</p>
  <input type="file" id="files" accept="application/pdf" multiple>
</div>

<div id="status"></div>
<div id="out"></div>

<footer>
  Nothing is uploaded. Files are read on this computer and deleted straight after.
  Everything shown comes from the documents themselves, with the page it came from.
  It does not replace reading the originals before you bid.
</footer>
</main>
<script>
const drop = document.getElementById('drop');
const input = document.getElementById('files');
const status = document.getElementById('status');
const out = document.getElementById('out');

drop.onclick = () => input.click();
drop.ondragover = e => {{ e.preventDefault(); drop.classList.add('over'); }};
drop.ondragleave = () => drop.classList.remove('over');
drop.ondrop = e => {{
  e.preventDefault(); drop.classList.remove('over');
  send(e.dataTransfer.files);
}};
input.onchange = () => send(input.files);

function esc(s) {{
  return (s || '').replace(/[&<>"]/g, c => ({{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}})[c]);
}}

async function send(fileList) {{
  const files = [...fileList].filter(f => f.name.toLowerCase().endsWith('.pdf'));
  if (!files.length) {{ status.textContent = 'Those are not PDF files.'; return; }}
  out.innerHTML = '';
  status.textContent = 'Reading ' + files.length + ' document(s)… a large bidding document takes a moment.';

  const fd = new FormData();
  files.forEach(f => fd.append('file', f, f.name));

  try {{
    const res = await fetch('/scan', {{ method:'POST', body: fd }});
    if (!res.ok) throw new Error('Scan failed (' + res.status + ')');
    render(await res.json());
  }} catch (err) {{
    status.textContent = 'Could not read those files: ' + err.message;
  }}
}}

function render(r) {{
  status.textContent = r.summary;
  let h = '';

  if (r.facts.length) {{
    h += '<h2 class="sec">Key terms</h2>';
    for (const f of r.facts) {{
      const e = f.evidence;
      h += '<div class="fact"><div class="fh"><span class="lab">' + esc(f.label) + '</span>'
        + (f.value ? '<span class="val">' + esc(f.value) + '</span>' : '')
        + '<span class="conf c-' + f.confidence + '">' + esc(f.conf_label) + '</span></div>'
        + (f.plain ? '<p class="plain">' + esc(f.plain) + '</p>' : '')
        + '<p class="where">page ' + e.page
        + (e.clause_no ? ', clause ' + esc(e.clause_no) : '')
        + ' — ' + esc(e.method) + ' — ' + esc(e.document) + '</p>'
        + '<blockquote>' + esc(e.quote.slice(0, 400)) + '</blockquote></div>';
    }}
  }}

  if (r.specs.length) {{
    h += '<h2 class="sec">Technical specification (' + r.specs.length + ')</h2><table>'
       + '<tr><th>Item</th><th>Required</th><th>Page</th></tr>';
    for (const s of r.specs)
      h += '<tr><td>' + esc(s.name) + '</td><td>' + esc(s.value) + '</td><td>p' + s.page + '</td></tr>';
    h += '</table>';
  }}

  if (r.unreadable.length) {{
    h += '<h2 class="sec">Could not read</h2>';
    for (const u of r.unreadable) h += '<p class="note">' + esc(u) + '</p>';
  }}

  if (r.report_url) {{
    h += '<p style="margin-top:26px"><a class="btn" href="' + r.report_url
      + '" target="_blank">Open printable report</a></p>';
  }}
  out.innerHTML = h;
}}
</script></body></html>"""


class Handler(BaseHTTPRequestHandler):
    # Quiet: the console is for the user, not a request log.
    def log_message(self, *args):        # noqa: D102
        pass

    def _send(self, code: int, body: bytes, ctype: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:            # noqa: N802
        path = urlparse(self.path).path
        if path == "/":
            self._send(200, PAGE.encode("utf-8"), "text/html; charset=utf-8")
        elif path.startswith("/font/"):
            self._serve_font(path.rsplit("/", 1)[-1])
        elif path.startswith("/report/"):
            self._serve_report(path.rsplit("/", 1)[-1])
        else:
            self._send(404, b"Not found", "text/plain")

    def _serve_font(self, which: str) -> None:
        name = {"regular": "celias_regular.otf", "light": "celias_light.otf"}.get(which)
        f = ROOT / "app/src/main/res/font" / (name or "")
        if name and f.exists():
            self._send(200, f.read_bytes(), "font/otf")
        else:
            self._send(404, b"", "font/otf")

    def _serve_report(self, name: str) -> None:
        f = Path(tempfile.gettempdir()) / "tenderscan" / name
        if f.exists() and f.suffix == ".html":
            self._send(200, f.read_bytes(), "text/html; charset=utf-8")
        else:
            self._send(404, b"Report expired", "text/plain")

    def do_POST(self) -> None:           # noqa: N802
        if urlparse(self.path).path != "/scan":
            self._send(404, b"Not found", "text/plain")
            return
        try:
            self._handle_scan()
        except Exception as e:           # noqa: BLE001
            # A bad PDF must never take the app down - report and carry on.
            msg = json.dumps({"error": f"{type(e).__name__}: {e}"}).encode()
            self._send(500, msg, "application/json")

    def _handle_scan(self) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_UPLOAD:
            self._send(413, b'{"error":"file too large"}', "application/json")
            return

        ctype = self.headers.get("Content-Type", "")
        boundary = ctype.split("boundary=")[-1].strip('"') if "boundary=" in ctype else None
        if not boundary:
            self._send(400, b'{"error":"bad upload"}', "application/json")
            return

        body = self.rfile.read(length)
        files = _parse_multipart(body, boundary.encode())
        if not files:
            self._send(400, b'{"error":"no PDF received"}', "application/json")
            return

        workdir = Path(tempfile.mkdtemp(prefix="tenderscan_"))
        paths: dict[str, str] = {}
        try:
            for name, data in files.items():
                safe = "".join(c for c in name if c.isalnum() or c in "._- ")[:120] or "file.pdf"
                p = workdir / safe
                p.write_bytes(data)
                paths[safe] = str(p)

            result = extract_all(paths)
            payload = result.to_dict()
            for f in payload["facts"]:
                f["conf_label"] = CONF_LABEL.get(f["confidence"], f["confidence"])

            # A printable, watermarked copy, kept only for this session.
            reports = Path(tempfile.gettempdir()) / "tenderscan"
            reports.mkdir(exist_ok=True)
            rname = f"report_{abs(hash(tuple(paths))) % 10**8}.html"
            write_html(result, reports / rname, next(iter(paths), "Tender"))
            payload["report_url"] = "/report/" + rname

            self._send(200, json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                       "application/json; charset=utf-8")
        finally:
            # The documents are deleted as soon as they have been read.
            for p in paths.values():
                try:
                    os.remove(p)
                except OSError:
                    pass
            try:
                workdir.rmdir()
            except OSError:
                pass


def _parse_multipart(body: bytes, boundary: bytes) -> dict[str, bytes]:
    """Minimal multipart reader, so the app needs nothing installed beyond Python."""
    out: dict[str, bytes] = {}
    for part in body.split(b"--" + boundary):
        if b"\r\n\r\n" not in part:
            continue
        head, _, data = part.partition(b"\r\n\r\n")
        if b"filename=" not in head:
            continue
        try:
            name = head.split(b"filename=")[1].split(b'"')[1].decode("utf-8", "replace")
        except IndexError:
            continue
        data = data.rstrip(b"\r\n-")
        if name and data[:4] == b"%PDF":
            out[name] = data
    return out


def _free_port() -> int:
    with socket.socket() as s:
        s.bind((HOST, 0))
        return s.getsockname()[1]


def main() -> int:
    port = _free_port()
    server = ThreadingHTTPServer((HOST, port), Handler)
    url = f"http://{HOST}:{port}/"

    print("=" * 58)
    print("  TENDER SCANNER")
    print("=" * 58)
    print(f"  Open:  {url}")
    print("  Everything stays on this computer. Nothing is uploaded.")
    print("  Close this window to quit.")
    print("=" * 58)

    threading.Timer(0.8, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nClosed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
