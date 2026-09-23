# App Store listing

Everything App Store Connect asks for, written and ready to paste. `{{TOKEN}}`
values come from `docs/submission-placeholders.md`. Review notes are separate, in
`docs/review-notes.md`.

Wording rule from PLAN §7.2, which the copy below already follows: say "expense
tracking", "spending log", "personal budgeting". Never "money management",
"banking", "financial advice" or "investment". Those words route the app to a
reviewer who will ask for a regulator's licence.

## App information

| Field | Value |
|---|---|
| Name (30 max) | `VITT` |
| Subtitle (30 max) | `Spending in every currency` (26) |
| Bundle ID | `ie.shoonya.vitt` |
| SKU | `vitt-ios-001` |
| Primary category | `{{PRIMARY_CATEGORY}}` |
| Secondary category | `{{SECONDARY_CATEGORY}}` |
| Price | `{{PRICE}}` |
| Age rating | 4+ |
| Copyright | `2026 {{APPLE_DEVELOPER_NAME}}` |

## URLs

| Field | Value |
|---|---|
| Privacy policy URL | `{{POLICY_URL}}` |
| Support URL | `{{SUPPORT_URL}}` |
| Marketing URL | `{{MARKETING_URL}}` |

## Promotional text (170 max)

```
Log what you spend in any currency, on a phone that keeps it. No account, no
bank link, no server. Connect a Google Sheet if you want a copy you own.
```

## Description (4000 max)

```
VITT is a spending log for people whose money is in more than one currency.

Earn in euro and spend in rupees, or the other way round, and most trackers give
you one blended number that is true of nothing. VITT keeps each currency whole.
Euro sits beside rupees, each with its own balance and its own budget, and a rate
is recorded only when a real transfer actually used one.

NO ACCOUNT

There is no VITT account and no VITT server. Nothing to sign up for, nothing to
cancel, no password to lose. Open the app and start.

YOUR DATA STAYS YOURS

Entries live on your phone. If you want a second copy, connect Google Drive and
VITT writes to one spreadsheet it creates in your own Drive. You own the file,
you can open it in any spreadsheet app, and you can delete it whenever you like.
VITT asks only for permission to touch files it created. It cannot see anything
else in your Drive.

WHAT IT DOES

Add a spend in a few taps, with a keypad built for amounts.
Separate balances and budgets per currency.
Accounts, so you know which card or bank an entry came from.
Categories that learn from what you have already logged.
Splits, for when you paid and someone owes you a share.
Transfers between accounts, including across currencies.
A monthly report: your pace, where the spending moved, income against spend.
CSV export of everything, any time.
Light and dark themes with a few accents.

WHAT IT DOES NOT DO

It does not read your SMS or your notifications.
It does not connect to your bank.
It does not import from your bank. You log what you spend.
It does not track your location.
There are no ads, no analytics, and no third-party tracking.
It does not give financial advice, hold funds, or move money.

VITT is a personal spending log. That is the whole of it.
```

## Keywords (100 max, comma separated, no spaces)

```
expense,spending,budget,currency,multicurrency,tracker,euro,rupee,csv,offline,privacy,spreadsheet
```

That is 97 characters. Do not repeat the app name or the category name: Apple
indexes both already and the space is better spent.

## What's New (first release)

```
First release.
```

## Screenshots

Captured, in `screenshots/6.9/`. Five of them, 1320x2868, which is the 6.9"
size: 1290x2796 is 6.7", and an earlier revision of this file had the two
confused. Taken from a seeded simulator, in the order that tells the story:

1. Ledgers, two currencies, budget bars showing.
2. Add, mid-amount, keypad open.
3. Reports, the month's pace.
4. Activity, a list with a split visible.
5. Settings, showing Google connect and the plain "no account, no server" line.

Re-capture with `SIMCTL_CHILD_VITT_SEED=1` on a 16 Pro Max, stacked card
layout, so the Ledgers shot shows two currencies rather than one and a peek.

## App privacy (the nutrition card)

Answer: **Data Not Collected.** Every question, every category.

This is true and it is checked against the binary. It holds because there is no
analytics SDK, no crash reporter, no ad SDK and no VITT server. The only network
calls are to Google's own APIs on the user's behalf, with the user's own
credentials, writing to the user's own file. `iosApp/iosApp/PrivacyInfo.xcprivacy`
already declares this and matches.

Adding any SDK that phones home invalidates this card and the manifest together.

## Age rating questionnaire (2026 wording)

All answers None or No. The app has no user-generated content, no chat, no web
browsing, no gambling, no contests, no ads. Result: 4+.

## Export compliance

`ITSAppUsesNonExemptEncryption` is already `false` in the Info.plist. The app uses
only HTTPS to Google's APIs, which is exempt. The upload will not stop to ask.

## Content rights

The app contains no third-party content. Answer No.
