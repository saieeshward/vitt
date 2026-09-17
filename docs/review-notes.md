# App Review notes

Paste into App Store Connect > App Review Information > Notes, and into the
Play Console test instructions. Fill the `<…>` items at submission time.
Written from the compliance audit on 2026-09-17; keep it true as the app changes.

```text
1. SCREEN RECORDING
Demo video URL: <record on the phone: add a spend, open Reports, connect Google, disconnect>

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
3. The Reports tab shows the month's pace, category moves and income against spend.
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

5. REGIONAL DIFFERENCES
This app works the same in every region.

6. REGULATED INDUSTRY DOCUMENTATION
Not applicable. VITT is a personal spending log. It offers no financial
product, holds no funds, moves no money and gives no advice.
```

Wording rule for the listing (PLAN §7.2): say "expense tracking", "spending
log", "personal budgeting". Never "money management", "banking", "financial
advice" or "investment". Age rating 4+. Privacy card: Data Not Collected.
