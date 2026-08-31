# Getting the scraper running 24/7 (free)

You said you don't have a GitHub account. Here is the whole path, in order.
Total time: about 15 minutes. Cost: nothing.

## Why GitHub at all?

The scraper has to run somewhere that isn't your PC, or it only catches tenders
while your machine is on. GitHub Actions gives you a free Linux machine that
wakes up every 30 minutes, runs the scrapers, and saves the results. It also
builds the Android APK for you, which means you never have to install Java or
Android Studio on your computer.

Free tier limits, so you know we are inside them:
- Public repository: **unlimited** Actions minutes.
- Our scrape takes roughly 5 minutes. Every 30 minutes = ~240 min/day.

A note on "public": a public repo means the *code* is visible. The tender data
is already public government information, so nothing sensitive is exposed.
Alert settings (your email password, WhatsApp token) are stored as encrypted
**Secrets**, never in the code. If you would rather keep the repo private,
say so - private repos get 2,000 free Actions minutes/month, which is about
one scrape every 3 hours rather than every 30 minutes.

## Step 1 - Create the account

1. Go to <https://github.com/signup>
2. Enter your email (`Vancott2@gmail.com` works), pick a password and a username.
   Something like `vancott-tech` is fine.
3. Verify the email they send you.

That's the whole signup. No card, no paid plan.

## Step 2 - Create the repository

1. Go to <https://github.com/new>
2. Repository name: `vancott-tenders`
3. Choose **Public** (see the note above).
4. Do NOT tick "Add a README" - the project already has files.
5. Click **Create repository**.

GitHub will then show you a page with commands. Ignore it; use Step 3 instead.

## Step 3 - Push the project up

You need Git on your PC. Check by opening PowerShell and running:

```bash
git --version
```

If that errors, install it from <https://git-scm.com/download/win> (all default
options are fine), then reopen PowerShell.

Then, from the project folder:

```bash
cd "$HOME/Desktop/VANCOTT-TENDERS"
git init
git add .
git commit -m "VANCOTT tender scraper"
git branch -M main
git remote add origin https://github.com/ShahzebJ8/vancott-tenders.git
git push -u origin main
```

Replace `ShahzebJ8` with the username you picked. Git will ask you to sign
in - a browser window opens, you approve, done.

## Step 4 - Turn the scraper on

1. In your repo on github.com, click the **Actions** tab.
2. It will say workflows are disabled for a new repo - click
   **"I understand my workflows, go ahead and enable them"**.
3. Click **Scrape tenders** in the left sidebar, then **Run workflow**.

It runs immediately, and every 30 minutes from then on. Green tick = it worked.
Click into a run to read the log and see how many tenders it found.

## Step 5 - Where your data lives

After the first successful run, the file `data/tenders.json` in your repo holds
every tender. The Android app reads it from:

```
https://raw.githubusercontent.com/ShahzebJ8/vancott-tenders/main/data/tenders.json
```

That URL is served by GitHub's CDN - fast from Pakistan, and free.

## If something breaks

Every run is logged under the Actions tab. A source that goes down shows up in
`data/tenders.json` under `sources` with `"status": "error"` and the reason.
The app displays that, so you always know whether a province genuinely has no
tenders or simply could not be reached. It never silently pretends.
