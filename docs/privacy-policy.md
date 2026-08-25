# Privacy policy

**Draft — not yet published. Placeholders marked `<…>` must be filled before this
goes live. Have it reviewed if you are unsure; it is a binding public statement
and both app stores check it against your declared data practices.**

*Applies to the VITT mobile app for Android and iOS.
Last updated: `<DATE>`. Contact: `<PRIVACY_EMAIL>`.*

## The short version

VITT has no server. We do not receive, store, or transmit your financial data.
Your data lives in two places: on your device, and in a Google Sheet in your own
Google Drive. We cannot see either one.

## What VITT collects

**Nothing is collected by us.** There is no VITT account, no VITT backend, and
no analytics or advertising SDK in the app.

Data you enter — transactions, amounts, merchants, categories, accounts, budgets,
notes — is stored:

1. **On your device**, in a local database, protected by your operating system's
   storage encryption (iOS Data Protection, Android File-Based Encryption).
2. **In your own Google Sheet**, if you choose to connect Google Drive. That file
   is in your Drive, owned by you, governed by
   [Google's Privacy Policy](https://policies.google.com/privacy), and deletable
   by you at any time.

Connecting Google Drive is **optional**. VITT is fully functional without it.

## Google account access

If you connect Drive, VITT requests two OAuth scopes:

| Scope | What it permits |
|---|---|
| `https://www.googleapis.com/auth/drive.file` | Create and modify **only** files VITT created, or a file you explicitly select. VITT cannot list, read, or search any other file in your Drive. |
| `openid`, `email`, `profile` | Read your email address and basic profile, to show which account the spreadsheet belongs to. |

VITT does **not** request `drive` or `spreadsheets` — scopes that would grant
access to your entire Drive or to all your spreadsheets.

Your Google access and refresh tokens are stored **only on your device**, in the
iOS Keychain or Android Keystore. They are never transmitted to us — we operate no
server to transmit them to.

### Withdrawing access

- **In VITT:** Settings → Disconnect Google. This revokes the token with Google
  and erases it from your device.
- **At Google:** [myaccount.google.com/permissions](https://myaccount.google.com/permissions).

Revoking access does not delete your spreadsheet; it remains yours.

## What VITT does not do

- Does not read your SMS messages.
- Does not read your notifications.
- Does not connect to your bank or ask for banking credentials.
- Does not track your location.
- Does not access your contacts, camera, microphone, or photos.
- Contains no advertising, no analytics, and no third-party tracking SDKs.
- Does not sell, share, or transfer your data to anyone, because it never
  receives it.
- Does not use your data to train machine-learning models.

## Optional features that transmit data

These are **off unless you turn them on**, and each is described where you enable
it:

- **Google Drive sync** — sends your transaction data to your own Google Sheet.
- **Parser diagnostics** — if enabled, sends anonymised transaction *text* that
  VITT failed to parse, with amounts and account numbers removed, so import rules
  can be improved. No amounts, no balances, no account identifiers, no user
  identifier. Off by default.

## Retention and deletion

We hold no data, so we have nothing to retain or delete.

- **Device data:** uninstalling VITT deletes it, as does Settings → Delete local
  data.
- **Spreadsheet data:** delete the file in Google Drive, or use Settings → Delete
  spreadsheet.

## Children

VITT is not directed at children under 13 and we knowingly collect no data from
anyone, of any age.

## Your rights

GDPR, the Irish Data Protection Act, and comparable laws grant rights of access,
rectification, erasure, and portability against a data controller. **For your
financial data in VITT, you are the controller** — the data is on your device and
in your Drive. Access it by opening the app or the spreadsheet; export it via
Settings → Export; erase it as described above. No request to us is needed, and we
could not fulfil one, as we hold nothing.

For the optional parser diagnostics, we act as controller for anonymised text
fragments that contain no identifier and cannot be traced to you — which also
means we cannot locate or delete an individual submission. Disable the setting to
stop further submissions.

## Changes

Material changes will be noted in the app's release notes and in this document's
revision history. The current version always lives at `<POLICY_URL>`.

## Contact

`<PRIVACY_EMAIL>`
