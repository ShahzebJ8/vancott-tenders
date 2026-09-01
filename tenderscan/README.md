# Tender Scanner

Reads a Pakistani tender PDF and pulls out the terms that decide whether you
can bid, and what you would be bidding on.

## Using it

Double-click **`Tender Scanner.bat`** in the project folder. It opens in your
browser. Drag a PDF in - or several at once, which are read as one tender.

From a terminal instead:

```bash
python tenderscan/scan.py "C:/path/to/tender.pdf" --html report.html
```

## What it finds

- Bid deposit (bid security), cost of the bidding papers, estimated value
- Guarantee if you win, price validity, delivery period, late penalty
- Submission deadline, opening time, who may bid, how to package the bid
- The full technical specification table - pitch, brightness, refresh rate,
  controller, and everything else a price is built from

## How much to trust it

Every finding is tagged:

| Tag | Where it came from |
|---|---|
| **certain** | a labelled table cell or an explicit "Label: value" line |
| **likely** | inside a numbered clause |
| **check this** | loose text - read the quoted line before relying on it |

Each one shows the page number and the exact sentence it came from. That is
deliberate: it is a reader, not an oracle, and a wrong reading should be
obvious rather than quietly believed.

It refuses to report a value it cannot stand behind - a "Bid Security: Yes"
checkbox is dropped rather than reported as an amount.

**It does not replace reading the tender documents before bidding.**

## Privacy

Runs on 127.0.0.1 only, so nothing outside this computer can reach it. No file
is uploaded anywhere, and documents are deleted as soon as they have been read.
