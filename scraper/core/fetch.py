"""HTTP layer: polite, persistent, and hard to block.

These are public government listings, not hardened endpoints. The approach is
to look exactly like the browser that the site expects, keep one session alive
(cookies included), back off on failure, and never hammer. That handles the
real-world "bot blocked" cases. We do NOT solve CAPTCHAs; a source that starts
demanding one is reported as degraded so a human can check it.
"""
from __future__ import annotations

import random
import ssl
import time
from typing import Optional

import requests
import urllib3
from bs4 import BeautifulSoup
from urllib3.util.ssl_ import create_urllib3_context

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

UAS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
]

BLOCK_MARKERS = ("just a moment", "attention required", "cf-browser-verification",
                 "are you a robot", "captcha", "access denied", "incapsula")


class Blocked(Exception):
    """Raised when a source demands human verification. Never guessed around."""


class LegacyTLSAdapter(requests.adapters.HTTPAdapter):
    """Some Pakistani government servers (eproc.punjab.gov.pk among them) run
    old TLS stacks that modern OpenSSL refuses with WRONG_VERSION_NUMBER.

    Naming the legacy ciphers explicitly makes the handshake succeed. Verified
    against the live Punjab portal: default context fails, this one returns 200.
    Certificate verification is off for these hosts only, because their certs
    are frequently expired or self-signed; the data is public either way.
    """

    CIPHERS = "AES128-SHA:AES256-SHA:DEFAULT@SECLEVEL=1"

    def init_poolmanager(self, *a, **kw):
        ctx = create_urllib3_context(ciphers=self.CIPHERS)
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        ctx.options |= 0x4          # OP_LEGACY_SERVER_CONNECT
        kw["ssl_context"] = ctx
        return super().init_poolmanager(*a, **kw)


class Fetcher:
    def __init__(self, delay: float = 1.2, timeout: int = 30, retries: int = 4,
                 legacy_tls: bool = False):
        self.delay = delay
        self.timeout = timeout
        self.retries = retries
        self.s = requests.Session()
        if legacy_tls:
            self.s.mount("https://", LegacyTLSAdapter())
            self.s.verify = False
        self.s.headers.update({
            "User-Agent": random.choice(UAS),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Accept-Encoding": "gzip, deflate, br",
            "Upgrade-Insecure-Requests": "1",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "none",
            "Sec-Fetch-User": "?1",
            "Cache-Control": "max-age=0",
        })
        self._last = 0.0

    def _throttle(self) -> None:
        # Jittered gap so the request pattern isn't machine-regular.
        wait = self.delay + random.uniform(0, self.delay * 0.6) - (time.time() - self._last)
        if wait > 0:
            time.sleep(wait)
        self._last = time.time()

    def get(self, url: str, *, referer: Optional[str] = None, **kw) -> requests.Response:
        headers = dict(kw.pop("headers", {}))
        if referer:
            headers["Referer"] = referer
            headers["Sec-Fetch-Site"] = "same-origin"
        last_err: Optional[Exception] = None
        for attempt in range(self.retries):
            self._throttle()
            try:
                r = self.s.get(url, headers=headers, timeout=self.timeout,
                               allow_redirects=True, **kw)
                if r.status_code in (429, 503):
                    time.sleep(2 ** attempt * 3)
                    continue
                r.raise_for_status()
                if any(m in r.text[:4000].lower() for m in BLOCK_MARKERS):
                    raise Blocked(url)
                return r
            except Blocked:
                raise
            except Exception as e:                      # noqa: BLE001
                last_err = e
                # New identity on retry — most transient blocks clear with this.
                self.s.headers["User-Agent"] = random.choice(UAS)
                time.sleep(2 ** attempt + random.uniform(0, 1.5))
        raise RuntimeError(f"GET failed after {self.retries} tries: {url} :: {last_err}")

    def soup(self, url: str, **kw) -> BeautifulSoup:
        return BeautifulSoup(self.get(url, **kw).text, "lxml")
