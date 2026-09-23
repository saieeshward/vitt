# Privacy policy

**Draft — not yet published. Every `{{PLACEHOLDER}}` below must be filled before
this goes live; `docs/submission-placeholders.md` lists them all in one place.
Have it reviewed if you are unsure. It is a binding public statement and both app
stores check it against your declared data practices.**

*Applies to the VITT mobile app for Android and iOS.
Last updated: {{POLICY_LAST_UPDATED}}. Contact: {{PRIVACY_EMAIL}}.*

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

There is exactly one, and it is **off unless you turn it on**:

- **Google Drive sync** — sends your transaction data to your own Google Sheet.
  Nothing reaches us, because there is no us to reach: the request goes from your
  phone to Google.

VITT has no diagnostics, telemetry or crash-reporting upload of any kind. If one
is ever added, this section and the store privacy declarations change together
or not at all.

## Retention and deletion

We hold no data, so we have nothing to retain or delete. There is no VITT account
to delete either, because none is ever created.

- **Device data:** uninstalling VITT deletes it, along with the stored Google
  token. Nothing survives the uninstall.
- **Google access:** Settings → Disconnect revokes the token with Google and
  erases it from the phone.
- **Spreadsheet data:** the spreadsheet is a file in your own Drive. Delete it
  there, at [drive.google.com](https://drive.google.com), like any other file.
  VITT cannot delete it for you: the `drive.file` scope lets it write the file,
  not remove it from your Drive.

## Children

VITT is not directed at children under 13 and we knowingly collect no data from
anyone, of any age.

## Your rights

GDPR, the Irish Data Protection Act, and comparable laws grant rights of access,
rectification, erasure, and portability against a data controller. **For your
financial data in VITT, you are the controller** — the data is on your device and
in your Drive. Access it by opening the app or the spreadsheet; export it via
Reports → Export everything as CSV; erase it as described above. No request to us is needed, and we
could not fulfil one, as we hold nothing.

## Changes

Material changes will be noted in the app's release notes and in this document's
revision history. The current version always lives at {{POLICY_URL}}.

## Contact

{{PRIVACY_EMAIL}}
