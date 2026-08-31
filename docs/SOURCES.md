# Source status — measured, not assumed

Every line here is the result of an actual request made on 2026-08-31, not an
assumption about what these sites "should" do.

## Working

| Source | URL | Coverage | Method |
|---|---|---|---|
| PPRA Federal (EPMS) | epms.ppra.gov.pk | **1,751 active tenders** | Server-rendered HTML, paged. Detail-page enrichment + PDF download. |
| PPRA Punjab | eproc.punjab.gov.pk | **1,307 active tenders** (matches the portal's own reported total) | ASP.NET WebForms. Needs legacy TLS ciphers + full form-state postback + "Next Page" walking. |

Federal EPMS is not federal-only in practice: 438 of its tenders are in Sindh
cities and 46 in KP, because federal organisations procure nationwide. So the
provinces below are a partial gap, not a total blind spot.

## Broken on the government's side (not fixable from here)

| Source | What happens | Evidence |
|---|---|---|
| SPPRA Sindh (new portal) | Unified EPADS API returns a SQL error for every request type | `Procedure or function GetLegencyOrganizations has too many arguments specified.` |
| SPPRA Sindh (legacy site) | PHP crash | `Fatal error: 'break' not in the 'loop' or 'switch' context in /home2/pprasind/public_html/spprastats.php on line 44` |
| KPPRA (new portal) | Same SQL error as Sindh | identical response |
| KPPRA (own site) | Connection timeout | `www.kppra.gov.pk` did not respond |
| BPPRA Balochistan | HTTP 500 | `bppra.gob.pk/Tenders` |
| AJK / GB portals | Same SQL error as Sindh | identical response |

### The unified API, for the record

All provincial EPADS portals share one backend:

```
POST https://Apiprd.eprocure.gov.pk/websiteportal/publicportal/1.0.0/api/v1/publicportal/getpaydata
Headers: Authorization: Basic <public client credential>, OfficeDetail: <region>
Body:    {"Type":"Tenders","ID":0,"loggedInUserID":1,"loggedInUserOfficeID":<id>,"pagination":{...}}
```

Regions: `F-PPRA-Dev`, `Punjab-PPRA-Dev`, `Sindh-PPRA-Dev`, `KPK-PPRA-Dev`,
`B-PPRA-Dev`, `AJK-PPRA-Dev`, `GB-PPRA-Dev`. Office ids: Sindh `31640`, all
others `1233`.

`F-PPRA-Dev` returns real records. Every provincial region returns the SQL
error above. The credential is the one the public website ships in its own
JavaScript and serves public procurement data — the same data the site displays.

**This is worth re-testing periodically.** When PPRA fixes their backend, every
province turns on at once with no new scraper — just a region list. The runner
records each source's status in `data/tenders.json`, so a province coming back
online is visible immediately.

## Not yet built

- Private / individual LED-wall requests (OLX Pakistan, B2B boards)
- Newspaper classified tender pages

## Deliberately excluded

- **Facebook groups.** Reaching them requires a logged-in account. Scraping
  with your account violates Meta's terms and risks it being banned. Not worth
  your account for a handful of leads.
