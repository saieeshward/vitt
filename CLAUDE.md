# Tender — working notes for Claude

Multi-currency expense tracker. Kotlin Multiplatform, no backend, user's own Google
Sheet as storage. Read `README.md` for what the product is and why; this file is
about how to work in the repo.

## Repo state (keep this accurate)

Pre-alpha, Phase 1 complete. Four modules exist: `:shared`, `:composeApp` (KMP
library holding the shared Compose UI), `:androidApp` and `iosApp/` (thin shells that
only host it). Display name is **VITT**; the package namespace stays
`ie.shoonya.vitt`.

```
shared/src/commonMain/kotlin/ie/shoonya/vitt/
  capture/   AmountParser, CsvImport
  money/     Money, AmountEntry  — minor-unit integers, never Double
  sync/      Hlc, Iso8601, Event, EventLog, Outbox
composeApp/src/commonMain/.../ui/   AmountKeypad, SpikeApp
```

Build constraints worth knowing before touching Gradle are in
[docs/running-locally.md](docs/running-locally.md) — AGP 9 cannot apply
`com.android.application` alongside the KMP plugin, which is why the modules split
the way they do.

## Commands

```bash
./gradlew :shared:jvmTest          # fast loop — use this
./gradlew :shared:allTests         # includes iOS targets, needs macOS + Xcode
./gradlew :shared:compileKotlinJvm # compile check only
```

Prefix shell commands with `rtk` (see global instructions).

## Conventions

- Package namespace is `ie.shoonya.vitt`, matching the bundle id and applicationId.
  `shoonya` is a personal namespace spanning several repos; `vitt` is this project
  within it. (An earlier revision of this file said the namespace must never match
  the product name — that was reversed deliberately on 2026-08-26.)
- Dependencies go through `gradle/libs.versions.toml`. Never hardcode a version in a
  `build.gradle.kts`.
- New shared logic lands in `commonMain` with tests in `commonTest`. Reach for
  `expect`/`actual` only when a platform API genuinely differs.
- Money is integer minor units. Floating point never touches an amount.

## Hard constraints — do not propose otherwise

These are closed decisions from `PLAN.md` §0. Reopening them is a project-level risk,
not a design preference.

1. **No app-side account, ever.** Defeats Apple 4.8, Apple 5.1.1(v), and Play's
   data-deletion-URL requirement simultaneously. Adding one reopens all three.
2. **OAuth scopes are `drive.file` + `openid email profile`.** Never `spreadsheets`
   or `drive` — those are sensitive scopes and trigger CASA, verification, and a
   100-user cap.
3. **Never change the OAuth client ID.** `drive.file` grants are per (file × client
   ID); changing it destroys every user's grant unrecoverably.
4. **No notification or SMS reading.** Cut as a Play Sensitive Permissions violation.
   Capture is share-sheet, widget, and CSV/OFX import (§6).
5. **The sheet is an append-only log, never a computation the app trusts.** The Sheets
   API has no CAS, etag, or conditional write. The local DB is the sole source of
   truth. The app never edits a row it did not just write.
6. **No blended exchange rates.** Balances display natively per currency. The only
   recorded rate is one an actual cross-border transfer used.

## Where the answers live

`PLAN.md` is ~600 lines — grep it, don't read it whole.

| Topic | Section |
|---|---|
| Closed decisions, research findings | §0 |
| Sheets tabs, append-only log, schema | §2 |
| Client stack, CMP, SQLDelight, HLC | §3 |
| Gamification (Gentle tier only, off switch first) | §5 |
| Transaction capture | §6 |
| Store compliance | §7 |
| Phase order — what to build next | §9 |
| Still undecided | §10, §11 |

`docs/phase-0-checklist.md` tracks long-lead items; `[you]` items need the maintainer
and cannot be automated.
