# Security policy

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting: the **Security** tab → **Report a
vulnerability**. That channel is private to the maintainers.

If it is unavailable, email the address in the repository profile with `SECURITY`
in the subject.

Please include what you were able to do, how to reproduce it, and the app and OS
versions. A proof of concept helps.

Expect an acknowledgement within 5 days and an assessment within 14. There is no
bounty — this is an unfunded project — but every valid report is credited in the
release notes unless you prefer otherwise.

## Scope

In scope: the app, the sync engine, the Sheets integration, the OAuth flow, local
data storage, and the release pipeline.

Out of scope: vulnerabilities in Google Sheets, Drive, or Google's OAuth service
(report those to Google); anything requiring a physical unlocked device plus
attacker-installed software; and social engineering of users.

## Design notes relevant to security

- Tender has **no server** and **no Tender account**. There is no central store to
  breach.
- OAuth tokens live in the iOS Keychain or Android Keystore, never on a server
  and never in shared preferences.
- The `drive.file` scope means a compromised token grants access only to the
  spreadsheet Tender created, not the user's wider Drive.
- Financial data at rest relies on platform encryption — iOS Data Protection and
  Android File-Based Encryption. The local database is not separately encrypted;
  see [PLAN.md](PLAN.md) §3.1 for the reasoning and the conditions under which
  that should change.
- Anything a user can see in their own spreadsheet, they can also edit. Tender
  treats hand-edits as expected input, not as tampering.
