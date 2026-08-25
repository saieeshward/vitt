# VITT

*Very Interesting Transaction Tracking*

A free, open-source expense and net-worth tracker for people whose money lives
in more than one currency.

> **Status: pre-alpha.** Nothing is shippable yet. See [PLAN.md](PLAN.md) for the
> engineering plan and [docs/phase-0-checklist.md](docs/phase-0-checklist.md) for
> what is in flight.

## What makes it different

- **No exchange-rate blending, ever.** Balances are shown natively per currency
  — "EUR €X, INR ₹Y" — never merged into one estimated total. Your net worth
  does not appear to change because a rate moved. The single exception is a real
  cross-border transfer, which records the rate actually used, because that is a
  fact rather than an estimate.
- **Your data is a spreadsheet you own.** VITT stores everything in a Google
  Sheet in your own Drive. You can open it, read it, edit it, export it, or stop
  using VITT entirely and keep every row.
- **Offline-first.** The app works with no network. Nothing waits on a sync.
- **No accounts, no server.** VITT has no backend. There is no VITT account to
  create and no VITT database holding your spending.
- **No bank logins.** No aggregator, no credential sharing, no OTP walls.

## How it works

The app keeps a local database as its source of truth and appends every change to
an event log in your spreadsheet. Because the log is append-only, edits made on
two devices — or made offline for a week — merge without conflict or data loss.

Google's Sheets API offers no compare-and-swap, so an append-only log is not
merely tidy here; it is the only design that is safe. See [PLAN.md](PLAN.md) §2–3.

## Permissions

VITT requests exactly two OAuth scopes:

| Scope | Why |
|---|---|
| `drive.file` | Create and edit **only** the spreadsheet VITT made, or one you explicitly pick. VITT cannot see anything else in your Drive. |
| `openid email profile` | Identify which Google account the spreadsheet belongs to. |

VITT does **not** request `drive` or `spreadsheets`, which would grant access to
your whole Drive or all of your spreadsheets.

VITT does **not** read your SMS or your notifications.

## Platforms

Android and iOS, from one Kotlin Multiplatform codebase with a shared Compose
Multiplatform UI.

## Building

Requires JDK 17+, Android Studio, and — for the iOS target — macOS with Xcode.

```
./gradlew :composeApp:assembleDebug        # Android
./gradlew :shared:allTests                 # shared logic tests
open iosApp/iosApp.xcodeproj               # iOS
```

## Distribution

Play Store and App Store builds are signed by the maintainers. GitHub Releases
carry a self-signed universal APK for sideloading, also available through
[Obtainium](https://github.com/ImranR98/Obtainium).

**A GitHub APK and a Play install use different signing keys and cannot update
each other.** Pick one and stay on it. Certificate fingerprints are published in
[docs/verifying-builds.md](docs/verifying-builds.md).

## Licence

[Apache-2.0](LICENSE). The name and icon are not covered — see
[TRADEMARK.md](TRADEMARK.md).
