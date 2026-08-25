# Contributing

## Licensing (read first)

Contributions are accepted **inbound = outbound**: anything you submit is
licensed under [Apache-2.0](LICENSE), the same licence as the project. There is
no CLA. By opening a pull request you confirm you have the right to license the
code that way.

This matters more than it usually does. A single contributor holding rights under
a different licence can make the app un-shippable on the App Store, and it cannot
be undone retroactively. If you cannot agree to Apache-2.0, please open an issue
instead of a PR.

## Environment

| Requirement | Version |
|---|---|
| JDK | 17+ |
| Android Studio | current stable |
| Xcode | current stable — **macOS only** |
| Kotlin / CMP | pinned in `gradle/libs.versions.toml` |

**iOS work requires a Mac.** Android and shared-logic work does not. Plenty of
useful contribution needs no Mac at all — the sync engine, parsers, and
categorisation all live in `commonMain` and test on any platform.

## Before you start

- **Open an issue first** for anything beyond a bug fix. It saves you writing
  code that does not fit the plan.
- Read [PLAN.md](PLAN.md). It records not just decisions but why, and several
  attractive-looking features are deliberately excluded.

## Things that will not be merged

These are not oversights. Each is documented in PLAN.md with sources.

- **Notification or SMS reading** to capture transactions. It violates Google
  Play's Sensitive Permissions policy, which forbids alternative methods of
  deriving SMS-attributed data. It puts the listing at risk.
- **Requesting broader OAuth scopes** than `drive.file`. Anything wider triggers
  Google verification and a recurring paid security assessment.
- **Converting currencies into a single blended total.** This is the one thing
  the project exists to avoid.
- **Syncing derived values** — balances, budget rollups, net worth. They are
  computed locally from transactions. Syncing them produces balances that
  disagree with the transactions beneath them.
- **Leaderboards, percentile comparisons, or social features.** The evidence is
  that they harm below-median users and help no one; see PLAN.md §5.5.
- **Streaks that reset to zero, punishments, or guilt-based copy.** Same section.
- **Randomised rewards** of any kind, including cosmetic loot boxes.
- **An in-app account system.** It would trigger three separate store
  requirements at once.

## Code

- Kotlin official style; `./gradlew ktlintCheck detekt` must pass.
- Money is **integer minor units** (`Long`), never `Double` or `Float`. There is
  a `Money` type; use it.
- Business logic belongs in `commonMain` so it can be tested cheaply. Platform
  code goes behind `expect`/`actual`.
- New sync-engine or parser logic needs unit tests. These are the two places
  where a bug silently corrupts someone's financial records.
- Prefer clarity to cleverness. This code will be read by people auditing where
  their money data goes.

## Commits and PRs

Conventional Commits — `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
Release automation derives the changelog and version from these, so the prefix
matters. Breaking changes use `!` or a `BREAKING CHANGE:` footer.

Keep PRs focused. Say what you changed and why. Note any platform you could not
test on.

## Releases

Store releases are maintainer-only — they need signing keys that cannot be shared.
Merged work ships in the next tagged release.
