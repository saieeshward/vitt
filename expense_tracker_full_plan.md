# Multi-Currency Personal Expense & Net Worth Tracker — Full Plan

*Consolidated from full planning conversation, last updated August 25, 2026*

## 1. The Idea

A free, personal, cross-platform (iOS + Android) app to track income, expenses, and net worth across any number of bank accounts, countries, and currencies — starting with EUR (Ireland) and INR (India) accounts — using each user's own Google Sheet as the entire backend, so it costs nothing to run. Built primarily for personal use, with the recognition that many people living/earning across countries share this exact problem.

## 2. Validation

Google Sheets as a live backend has real precedent (Tiller Money, several open-source projects). The multi-currency "expat" niche is an active market (FiniQ, Vello, Roam, Toshl, PocketSmith, Lunch Money) — not something to compete head-on with, but proof the underlying need is real and shared beyond one person.

## 3. Data Architecture

**One spreadsheet per user**, flat tables, no sheet-per-currency or sheet-per-month (a documented anti-pattern that fragments rollups and requires manual re-merging every time a new currency/account appears).

Tabs:
- **Accounts** — name, country, currency, bank. Adding a new account is just a new row.
- **Transactions** — the single source of truth: date, account, amount, currency, category, split fields, description.
- **Categories** — the fixed taxonomy (see §7).
- **Rules** — description/merchant → category mappings (see §7).
- **Ledger** — a live filtered view of unsettled owed amounts.
- **Dashboard** — built entirely from `SUMIFS` / `QUERY` / Pivot Tables that auto-expand to cover new currencies or accounts with zero reconfiguration.

**Currency policy: no conversion, ever.** Balances are shown natively, grouped by currency ("EUR: €X", "INR: ₹Y"), never blended into one estimated total — this avoids the "FX drift" problem where net worth appears to change just because exchange rates moved. The one exception: an actual cross-border **Transfer** is its own transaction type that locks in the real historical rate used at the time, since that's a fact, not an estimate.

**Reporting**: any custom date range (weekly, calendar month, or a personal cycle like the 20th-to-20th) via `QUERY` filtered on two date cells, plus automatic recurring digests via Google Apps Script time-driven triggers (`MailApp.sendEmail`).

## 4. The Reimbursement Ledger

A simple boolean "Split this expense?" toggle — not full group-expense accounting. Enter Total Paid and Your Share; the app tracks the difference as an owed amount until settled. Owed amounts surface passively on dashboard open ("You're owed €16 — mark as received?"), not via push notifications.

## 5. Automatic Capture — Scoped After Research

- **SMS-reading**: dropped entirely. iOS has no API for it at all; Android restricts it to the device's default SMS handler — disproportionate scope for a budget app.
- **Notification-reading**: kept as an *optional* convenience, differing by platform. iOS: a user-configured Shortcuts automation that feeds a scoped app action (per source app, not blanket access). Android: `NotificationListenerService`, which is technically broader access even with disciplined code — a flagged, accepted trade-off, not a hidden one.
- **Manual entry** remains the universal, fully-supported baseline on both platforms.

## 6. Architecture Principles

**Offline-first**: a local on-device database is the source of truth for daily use. Every add/edit writes locally first and reflects instantly in the UI. A background sync engine batches writes to the user's Google Sheet (via `batchUpdate`/`batchGet`, never per-keystroke) whenever connectivity is available.

## 7. Categorization — Manual + Auto, Three Tiers

**Taxonomy**: 8–12 top-level categories, assigned by *purpose* not merchant (a work coffee and a personal coffee are both "Food"). Suggested default set: Groceries, Dining, Transport, Housing/Rent, Utilities, Health, Shopping, Entertainment, Travel, Gifts/Family, Subscriptions, Income, Miscellaneous. Categories are currency-agnostic — one shared list across every account and currency.

**Tier 1 — Remembered rules** (deterministic, free): every manual correction the user makes is stored as a description/merchant → category rule in the Rules tab. Next time a similar description appears, it's pre-filled automatically.

**Tier 2 — Seed keyword rules** (pre-shipped, generic): a small starter rule set (uber/ola/taxi → Transport, swiggy/zomato/deliveroo → Dining, rent → Housing) so first use isn't a blank slate. These get increasingly overridden by Tier 1 as the user corrects things.

**Tier 3 — Manual fallback**: when nothing matches, the user picks manually, and that choice becomes a new Tier 1 rule — so the system needs less manual input over time without any ML model, training data, or hosting cost.

This applies to both entry paths: typed manual entry (auto-suggest as you type) and notification-based auto-log (pre-filled category before the "confirm before committing" prompt). Categorization always applies to **your share** only on split transactions, not the full paid amount. Because matching is on transaction text, not currency, the same rules work identically across EUR, INR, AED, or any other currency.

## 8. Gamification Layer

Research on gamified personal finance apps shows game elements (points, badges, progress bars, streaks) increase motivation and make users feel more competent and in control, and daily streaks/milestones help turn logging into an automatic habit rather than a chore. Existing precedent apps (Bento Money, Penny Pals, FiPet, Pawket) already tie a virtual pet's mood directly to budgeting behavior.

Planned mechanics:
- **Per-currency budget health bar**: green/yellow/red based on projected month-end status against a user-set limit, per currency (no blending across currencies).
- **Virtual pet**: mood states (happy/neutral/stressed/sick) driven by health bar status and daily check-in streaks.
- **Streaks**: daily check-in counter, soft cosmetic rewards at 3/7/30-day milestones.
- **Quests**: simple weekly, per-currency challenges auto-generated from existing categories/limits ("stay under dining budget this week").
- **Rewards**: cosmetic only (outfits, backgrounds, new pets) — no real-money or gambling mechanics, to avoid dark patterns.
- **Tone**: gentle, non-punitive micro-copy ("Spending is a bit high" rather than "You failed").

This gives an intuitive, non-arbitrary signal of whether the user is saving, without needing a single converted net-worth number.

## 9. Tech Stack

Compared Kotlin Multiplatform + Compose Multiplatform, Flutter, and React Native against the stated priorities (lightweight, long-term support, minimal dependency churn). **KMP + Compose Multiplatform** won: it compiles to native machine code (no JS bridge), was declared production-stable — including on iOS — as recently as May 2025, and avoids the kind of forced breaking migration React Native underwent in 2026 when it removed its old bridge entirely (RN 0.85, April 2026, mandatory TurboModules migration). Flutter is the solid second choice; React Native ranked lowest specifically against the "minimal dependency issues through updates" requirement.

## 10. Store Readiness

Confirmed KMP/Compose Multiplatform is store-legal on iOS — Apple's Guideline 2.5.2 targets interpreted/downloaded code, not compiled native binaries, and published Compose Multiplatform iOS apps already exist.

Checklist:
- **iOS**: Sign in with Apple (mandatory alongside Google Sign-In), `PrivacyInfo.xcprivacy` manifest, accurate App Privacy card.
- **Android**: Target API 36 (Android 16) by Aug 31, 2026, Data Safety form matching real SDK data collection, foreground service types declared if the notification listener ships.
- **Both**: live privacy policy URL, seeded demo account for reviewers, Google OAuth sensitive-scope verification for Sheets access (budget ~1–2 weeks lead time), real device/beta testing before submission.
- **Costs**: Apple Developer $99/yr, Google Play Console $25 one-time.

## 11. Market Research — Why Others Fail, and the USP

Failure patterns found across existing multi-currency apps:
- **Single-currency architecture bolted onto multi-currency lives** (YNAB, Monarch) — forces separate budgets or manual conversion per foreign transaction.
- **Over-reliance on bank aggregation in hard markets** — Plaid-style aggregators are weak in India/Middle East/SEA, and OTP-heavy MFA breaks "automatic" sync.
- **FX drift** — apps that always show one converted total make net worth appear to swing from rate movement alone, not real spending.
- **Manual entry fatigue** — even well-reviewed apps (Toshl) see drop-off as friction accumulates, especially when auto-sync is paywalled.
- **Analytics that are either too basic or too complex** (full FX-exposure/balance-sheet detail) — missing the simple, human-friendly middle ground.

USP for this app:
1. **Truth-only multi-currency view** — no blended total, no FX drift, ever.
2. **User-owned backend** — a Google Sheet the user can open directly, not a proprietary server.
3. **Bank-agnostic by design** — no aggregator logins, no OTP walls to break.
4. **Simple reimbursement ledger** — solves the "I fronted the bill" problem without full group-expense accounting.
5. **Offline-first** — avoids stale-balance and ghost-data failure modes common to constantly-syncing apps.

## 12. Deliverables Produced

- Detailed Google Sheets schema
- Full-plan Markdown report (this document)
- `.clan` file matching the `saieeshward/clan` repo structure — manifest, context, output schema, decision log (15 entries), shared plan data, rendered human view

## 13. Still Open

- Sign in with Apple: identity-only, or a separate storage path for Apple-signed-in users who don't grant Google Sheets access?
- Ledger reminders: dashboard-only in v1, or push notifications later?
- Framework split: fully shared Compose Multiplatform UI vs. native SwiftUI/Jetpack Compose with shared Kotlin logic only?
- Final taxonomy: lock the exact 10-ish default categories and subcategories.
- Gamification v1 scope: one pet vs. multiple; how many quests per week by default?
