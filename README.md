# VANCOTT Tenders

Finds Pakistani government tenders, flags the SMD / LED ones, and puts the whole
searchable directory on your phone.

Built for VANCOTT (CH Waseem Manzoor) and staff. Not a portal, not a product —
an internal tool.

## What it does

1. **Scrapes** every active tender from the Pakistani procurement portals that
   actually work (see `docs/SOURCES.md` — measured, not assumed).
2. **Classifies** each one for SMD / LED relevance using word-boundary matching.
3. **Downloads** the real tender PDFs and records where the bid has to go —
   office address, contact person, email, bid security, opening time.
4. **Publishes** everything to `data/tenders.json`, which the Android app reads.
5. **Notifies** you when a new SMD tender appears.

Everything runs on free infrastructure. There is no server to pay for.

## The two rules this project keeps

**Nothing is ever invented.** If PPRA did not print a closing date, the field is
empty and the app says "not stated". A wrong deadline is worse than a blank one.

**Nothing is silently missing.** If a source breaks, its status is recorded in
the feed and shown in the app. You always know whether a province genuinely has
no tenders or simply could not be reached.

## Current coverage

| Source | Tenders | Notes |
|---|---|---|
| PPRA Federal (EPMS) | ~1,750 | Full detail: PDFs, contacts, bid security, corrigenda |
| PPRA Punjab | ~1,300 | Matches the portal's own reported total |

Sindh, KP, Balochistan, AJK and GB are **broken on the government's side** — their
shared API returns a SQL error and their own sites return PHP crashes and HTTP
500s. Details and the exact endpoint are in `docs/SOURCES.md`; when PPRA fixes
it, every province turns on at once.

Federal EPMS still covers a lot of those provinces in practice (438 Sindh-city
tenders, 46 in KP), because federal bodies procure nationwide.

## Layout

```
scraper/
  core/      fetch (TLS + retries), models, classifier, storage
  sources/   one module per portal
  run.py     runs everything, records per-source status
app/         Android app (Kotlin + Jetpack Compose)
data/        tenders.json + downloaded PDFs (committed, so git is the history)
docs/        SETUP-GITHUB.md, SOURCES.md
.github/     scrape every 30 min, build the APK
```

## Running the scraper by hand

```bash
python scraper/run.py --max-pages 60
```

Add `--only epms_ppra` to run a single source.

## Getting it running for real

See **`docs/SETUP-GITHUB.md`**. About 15 minutes, free, and it gives you both
the 24/7 scraper and the APK builds without installing anything on your PC.

One value must be changed after the repo exists: `FEED_URL` in
`app/build.gradle.kts`, which points the app at your own `tenders.json`.
