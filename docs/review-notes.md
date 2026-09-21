# App Review notes

Paste into App Store Connect > App Review Information > Notes, and into the
Play Console test instructions. `{{TOKEN}}` values come from
`docs/submission-placeholders.md`. Listing copy is separate, in
`docs/app-store-listing.md`.

Written from the compliance audit on 2026-09-17, re-checked against the app on
2026-09-21; keep it true as the app changes.

```text
1. SCREEN RECORDING
Demo video URL: {{DEMO_VIDEO_URL}}

2. APP PURPOSE
Problem it solves: people who earn or spend in more than one currency have no
single place to log spending without a bank link or an account with a third party.
Value it provides: a personal spending log that keeps each currency separate,
stored on the device and, if the person chooses, in a spreadsheet in their own
Google Drive. There is no VITT account and no VITT server.

3. ACCESS INSTRUCTIONS AND TEST CREDENTIALS
No account exists and none is needed. Every feature works without signing in.
1. Open the app. Tap + and enter an amount to add a spend.
2. The Ledgers tab shows balances per currency; swipe the cards or scroll.
3. The Reports tab shows the month's pace, category moves and income against
   spend, and exports everything as CSV.
4. Settings (top right) > Google > Connect is optional. It asks only for the
   drive.file scope, which lets the app create one spreadsheet in the reviewer's
   own Drive. Disconnect on the same screen revokes the token with Google and
   erases it from the device.
Test account: not applicable. Any Google account works for the optional step,
or skip it entirely.

4. EXTERNAL SERVICES
Google OAuth (accounts.google.com, oauth2.googleapis.com) / optional sign-in
Google Sheets API and Drive API (drive.file scope) / the user's own spreadsheet
No analytics, crash reporting, advertising or payment SDK is present.

5. ACCOUNT DELETION (Apple 5.1.1(v))
Not applicable. The app creates no account, so there is none to delete. The only
credential that exists is the optional Google token, which Settings > Google >
Disconnect revokes with Google and erases from the device. Uninstalling removes
everything else. No data of any kind reaches a server we operate, because we
operate none.

6. REGIONAL DIFFERENCES
This app works the same in every region.

7. REGULATED INDUSTRY DOCUMENTATION
Not applicable. VITT is a personal spending log. It offers no financial
product, holds no funds, moves no money and gives no advice.
```

App Review Information contact: {{SUPPORT_EMAIL}}.

Wording rule for the listing (PLAN §7.2): say "expense tracking", "spending
log", "personal budgeting". Never "money management", "banking", "financial
advice" or "investment". Age rating 4+. Privacy card: Data Not Collected.
