# Multi-Currency Expense Tracker — Engineering Plan v2

*Supersedes `expense_tracker_full_plan.md`. Written 2026-08-25 after four parallel research streams
(Google Sheets as a backend, KMP client stack + offline sync, gamification evidence base, store/OSS compliance).*

> **Read §0 first.** Four findings invalidate parts of v1. Everything else in this document follows from them.

---

## §0. What the research changed

| v1 assumption | Research verdict | Consequence |
|---|---|---|
| Android notification-reading is an "accepted trade-off" | **Play policy violation on its face.** The Sensitive Permissions policy forbids "alternative methods… to derive data attributed to Call Log or SMS related permissions" — `NotificationListenerService` for bank alerts is the named target. Android 15+ also redacts OTP-bearing notification content. | **Cut the feature.** Replaced by share-sheet + widget + CSV/OFX import (§6). This is existential risk for a solo dev: a suspended listing ends the project. |
| Request the `spreadsheets` OAuth scope | `spreadsheets` is **sensitive**; `drive.file` is **non-sensitive** and every Sheets v4 method accepts it. Google stated on the record (2019-05-31) that `drive.file` apps skip restricted verification and third-party security assessment. | **`drive.file` + `openid email profile` only.** Removes OAuth verification, CASA (~$540–4,500/yr, annual re-assessment), the 100-user cap, the 7-day refresh-token expiry, and the demo video. **Highest-leverage decision in the project.** |
| Dashboard built from live `SUMIFS`/`QUERY`, app reads it back | There is **no `version`, no etag, no If-Match, no conditional write** anywhere on the Sheets API. Compare-and-swap is impossible. Open-range `QUERY` costs ~1s per 20k empty rows. | Sheets is an **append-only log + human-readable mirror**, never a computation the app trusts. Local DB is the sole source of truth (§3). |
| Gamification (pet, streaks, quests) drives good behaviour | Evidence is thin-to-negative. Points for logging are engagement-contingent rewards (**d = −0.40**, Deci et al. 1999, 128 experiments). Leaderboards harm below-median users asymmetrically (Card et al. 2012, *AER*). The **SEC names "streaks"** in its digital-engagement-practices enumeration; Robinhood paid **$7.5M** (Massachusetts, Jan 2024) to discontinue celebration-tied-to-frequency and activity-driving notifications. | Keep a **much smaller** gamification layer built on the one robust finding — the **ostrich effect** — plus a real off switch (§5). |

**Two irreversible decisions to make now, before any code:**

1. **No app-side account, ever.** This single choice defeats Apple Guideline 4.8 (no Sign in with Apple needed), Apple 5.1.1(v) (no in-app account deletion), and Play's Data-deletion-URL requirement — all three at once. Adding an account later re-opens all three.
2. **Never change the OAuth client ID.** `drive.file` grants are stored per (file × client ID). Changing the client ID destroys every user's per-file grant, unrecoverably, and they must re-pick their spreadsheet.

---

## §1. Product shape

A free, open-source, offline-first expense and net-worth tracker for people living across currencies. The user's own Google Sheet is the durable store and the escape hatch; the app is the interface.

**Preserved from v1 (all validated):**
- **No FX blending, ever.** Balances shown natively per currency. A cross-border Transfer is its own type and locks in the historical rate actually used — that's a fact, not an estimate. This remains the genuine differentiator.
- **Offline-first**, local DB authoritative.
- **Three-tier categorization** (learned rules → seed keywords → manual). Deterministic, debuggable, no ML, no hosting cost.
- **Reimbursement ledger** — boolean split toggle, Total Paid vs Your Share, surfaced passively.
- **KMP + Compose Multiplatform.** Confirmed store-legal: Guideline 2.5.2 targets interpreted/downloaded code; CMP compiles to native via Kotlin/Native with Skia as compiled code in the bundle. CMP iOS went Stable in 1.8.0 (May 2025).

**Cut or changed:**
- Notification/SMS auto-capture — **cut** (§0).
- Live-formula dashboard as a data source — **demoted** to a human-readable mirror.
- Virtual pet + streaks + quests as designed — **replaced** by §5.
- Apps Script digests — **demoted**. Consumer Gmail allows only 90 min/day total trigger runtime and 100 email recipients/day, and a bound script cannot be installed via API (each user would have to manually enable the Apps Script API in their own Google settings — fatal onboarding). Ship in-app summaries instead; optionally copy a template that *already contains* the script.

---

## §2. Sheets architecture

### 2.1 The core constraint

No CAS, no upsert, no idempotency key, no conditional write. Therefore: **the app never edits a row it did not just write, and never trusts a number the sheet computed.**

That sounds restrictive. It's actually liberating — it forces an append-only event log, which makes write conflicts *impossible by construction* rather than merely handled.

### 2.2 Tabs

Two tiers. This split is load-bearing and comes straight from Tiller's production experience.

**Tier 1 — data tabs. Append-only. Never restructured, never overwritten, never reordered.**

| Tab | Purpose |
|---|---|
| `Events` | **The log.** Every mutation ever made, one row per changed field. The real source of truth on the sheet side. Hidden by default. |
| `Accounts` | name, country, currency, bank, opening balance, status |
| `Categories` | the taxonomy |
| `Rules` | learned merchant → category mappings |
| `_Meta` | schema version, app version, device registry, spreadsheet locale stamp |

**Tier 2 — derived tabs. Versioned, replaceable wholesale, archive-before-overwrite.**

| Tab | Purpose |
|---|---|
| `Transactions` | Materialized current state, written by the app. **This is what the user actually reads and edits.** |
| `Ledger` | Unsettled owed amounts |
| `Dashboard` | Charts and KPIs over a *pre-aggregated summary*, never over the raw range |
| `Summary_YYYY` | Per-month/category/currency aggregates, written by the app as values |

### 2.3 `Events` schema

```
event_hlc | device_id | entity | entity_id | field | value | written_at
```

- `event_hlc` — hybrid logical clock, fixed-width 46 chars, lexicographically sortable. **Primary key and idempotency key.**
- `entity_id` — client-minted **UUIDv7** (RFC 9562 §5.7); time-ordered, good index locality.
- `value` — tagged string: `0:` null, `N:` number, `S:` string. Keeps money out of JSON number coercion.
- Amounts stored as **integer minor units**. Never floats.

**Why per-field rows rather than per-transaction rows:** two devices editing different fields of the same transaction both win. Whole-row LWW would silently discard one device's entire edit.

### 2.4 Writes

Every sync is **one** `spreadsheets.batchUpdate` — all-or-nothing atomic, and one batch counts as one request against the 60/min/user quota.

```
batchUpdate([
  AppendCellsRequest  → Events        (the new event rows)
  UpdateCellsRequest  → Transactions  (materialized rows, at computed A1 ranges)
  UpdateCellsRequest  → Summary_YYYY  (recomputed aggregates, as values)
])
```

Rules:
- `insertDataOption: INSERT_ROWS` — `OVERWRITE` is the default and will clobber anything below the detected table.
- `valueInputOption: RAW` for the `Events` log (verbatim, no coercion); `USER_ENTERED` only for `Transactions` where native dates matter. **Pin and record the spreadsheet locale in `_Meta`** — dates are stored as serial decimals, so a locale mis-parse is arithmetic-silent, not an error. Tiller hard-coded US locale and documented it rather than solve this; do the same, but detect and warn on mismatch.
- **Idempotency:** mint the HLC *before* the first attempt. On ambiguous failure, re-read the tail of `Events` and look for that HLC rather than blind-retrying. Duplicates — not corruption — are the failure mode, and this eliminates them.
- **Never `append` immediately after `deleteDimension`** — a known Sheets bug produces row offsets because the delete isn't fully applied server-side.
- Retry with truncated exponential backoff + jitter: `min((2^n) + random_ms, 32s)`. Throttle to ~1 write/sec per spreadsheet on 503.

### 2.5 Reads and change detection

- Poll `drive.files.get(fileId, fields="version,modifiedTime")` — a monotonic counter that "reflects every change made to the file on the server." Cheap; false positives, never false negatives. Adaptive interval: foreground frequent, background rare.
- On change, re-read only affected ranges. **Closed ranges always** (`A2:G50000`, never `A:G`).
- `headRevisionId` and `md5Checksum` are null for Sheets — do not build on them.
- Drive `files.watch` push is infeasible serverless (needs a CA-signed HTTPS webhook plus channel renewal).

### 2.6 Handling direct user edits

This is the USP's cost: the user *will* edit the sheet by hand. Design for it as the normal case.

- **Address columns by header name, not index**, rebuilt from row 1 every session. Guard against duplicate headers. This is Tiller's #1 support issue and still the right call, because users insert columns constantly.
- Ignore unknown columns; treat missing optional columns as absent, not as an error. Users add their own columns and expect them preserved.
- On detected drift between `Transactions` and the fold of `Events`, the app **offers a diff** — "3 rows in your sheet differ from the app. Import the sheet's version / keep the app's / review each." Never silently overwrite the user's typing.
- Hand-edits are **replayed in the user's favour**: backfilled receipts can only help (§5.4).

### 2.7 Scale budget — with real numbers

| Threshold | What happens | Response |
|---|---|---|
| 10,000 rows | fine | — |
| ~50,000 rows | lag begins, driven by formulas not row count | archive prior years to `Transactions_YYYY` tabs |
| ~100,000 rows | consistently-cited breaking point | hard-split into a second spreadsheet |
| 10,000,000 cells | hard spreadsheet cap (blank cells count) | never approached if the above holds |

At ~15 columns, 50k transactions ≈ 750k cells — 7.5% of budget. **A decade of heavy use fits.**

Performance discipline, all evidence-backed:
- **Closed ranges only.** Open-range `QUERY` costs ~1 second per extra 20,000 empty rows.
- **Delete unused blank rows/columns.**
- **Value-paste history.** Removes it from the dependency graph entirely — highest-leverage single change.
- **Isolate volatiles** (`NOW`, `TODAY`, `RAND`, `RANDBETWEEN`) to one cell and reference it absolutely. Also avoid `INDIRECT`/`OFFSET`, which defeat dependency optimization.
- **Restrict conditional formatting to the top ~200 rows.** This — not row count — was the actual culprit in Tiller's field reports, where users run 47,000 rows with "no material performance impact."
- **Pivot tables beat many `SUMIFS`**; one `QUERY` beats a chain of `FILTER`+`SUMIF`.
- Dashboard reads `Summary_YYYY` (small, app-written values), never the raw range.

**Plan the exit.** Nobody scales *on* Sheets: Glide caps at 25k rows, AppSheet at ~5 MB, levels.fyi migrated to Postgres over two years. Keep an export path (SQLite/CSV/JSON) from v1 so the sheet is never a trap.

### 2.8 Schema migration

- `_Meta.schema_version`, plus forward-only idempotent migration steps, each guarded by a check of **actual current state** rather than the recorded version number.
- **Data tabs: additive only.** New columns at the right edge, by header name. Never reorder, never delete, never renumber, **never backfill** (Tiller established that users accept this).
- **New features arrive as new tabs**, not as changes to existing ones.
- **Derived tabs: archive-then-replace.** Offer "Archive & update" / "Overwrite" / "Keep mine," because users will have customized them.
- `developerMetadata` gives row identity that survives inserts and sorts — but its **30,000-char-per-sheet cap allows only ~700 tagged rows**. Use it as an optional index at most. The hidden UUID column is the identity store.

---

## §3. Client architecture

### 3.1 Stack

| Layer | Choice | Version (Aug 2026) |
|---|---|---|
| UI | **Fully shared Compose Multiplatform** | CMP 1.9.x–1.10.x |
| Local DB | **SQLDelight** | 2.3.2 |
| Networking | Ktor client | 3.5.2 |
| Serialization | kotlinx-serialization-json | 1.11.0 |
| Coroutines | kotlinx-coroutines | 1.11.0 |
| Navigation | Circuit *or* Decompose | 0.37.1 / 3.5.0 |
| DI | Koin | 4.2.2 |
| Swift interop | Skie | 0.10.14 |
| Flow testing | Turbine | 1.2.1 |

**Shared UI, decisively.** The screens are commodity — lists, forms, charts, settings. Sharing logic only means writing every screen twice, doubling the layer with the most churn. The large-company pattern (Netflix, Cash App, Forbes → shared logic, native UI) reflects large-company constraints: existing native codebases, platform teams, brand-critical design systems. None apply here.

CMP's weakest iOS surface is text input — and this is an amount-entry-heavy app. **Mitigation: a custom numeric keypad Composable for the money field.** Sidesteps the system IME entirely and is better UX for amounts anyway. Keep `UIKitView` as a per-screen escape hatch.

Other CMP iOS realities to budget for: +10–20 MB app size, slow Kotlin/Native link times, poor cross-boundary debugging, scroll physics not identical to `UIScrollView`. All acceptable; none blocking. **Swift export is still Alpha** — keep the Kotlin→Swift surface to a few facade classes.

**SQLDelight over Room KMP** because the sync engine needs hand-written SQL — per-field HLC comparison predicates, an append-only outbox with FIFO `AUTOINCREMENT` semantics, index tuning on `(state, seq)`. SQLDelight makes that first-class and compile-time-verified; Room makes you fight the DAO abstraction. (Realm is dead — Atlas Device Sync EOL'd Sep 2025.)

**Encryption:** rely on platform at-rest protection (iOS Data Protection, Android FBE). Keychain/Keystore for the OAuth refresh token only. Skip SQLCipher in v1 — this app stores amounts and merchant names, not credentials. Note `EncryptedSharedPreferences` was deprecated at 1.1.0-alpha07; go straight to Keystore.

### 3.2 Sync engine

Adapted from the standard client↔server design, with the server removed. **Sheets is a dumb append-only log; all resolution happens client-side on fold.** This is *why* the append-only design is mandatory rather than merely tidy — there is no server to arbitrate.

```
UI  ──►  local SQLite (authoritative)  ──►  outbox  ──►  Sheets Events (append-only)
                    ▲                                            │
                    └────────── fold(all events, HLC order) ◄─────┘
```

**Local write path.** Local apply + outbox insert in **one synchronous transaction**. Mutate the in-memory HLC only *after* the transaction commits, or a rollback leaves the clock ahead of the log.

**Clocks — HLC, not `updated_at`.** Kulkarni et al., OPODIS 2014. `updated_at` measures *arrival*, so a device offline a week reconnects and its stale edits beat yesterday's — silently resurrecting old amounts. HLC is causality-respecting, O(1) space, and stays within bounded drift of wall-clock time, so it doubles as a human-readable "since" cursor.

```
send:     l' := l;  l := max(l', pt);  c := (l == l') ? c+1 : 0
receive:  l' := l;  l := max(l', l.m, pt)
          if (l == l' == l.m) c := max(c, c.m)+1
          elseif (l == l')    c := c+1
          elseif (l == l.m)   c := c.m+1
          else                c := 0
compare:  lexicographic on (l, c, device_id)
```

Note the absence of a Lamport `+1` in `send` — that's what preserves bounded drift. Encode fixed-width so comparison is plain string `>=`:

```
2026-08-25T22:23:42.123Z-0000-A219E7A71CC18912
└──── ISO millis (l) ────┘ └c┘ └─ device_id ─┘
```

Enforce a 5-minute hard drift limit.

**Conflict resolution: per-field last-write-wins.** This *is* a CRDT — a map of LWW-registers is convergent — just the cheap one, with O(1) metadata per field instead of O(edits).

Reject Automerge/Yjs/Loro: their value is character-level text merge, which this app doesn't have, and opaque binary blobs would destroy the sheet's human readability, which is the entire USP.

Field-specific rules:
- `amount` — LWW is **correct**. Never a counter CRDT: two devices "fixing" to the same value must not sum to double.
- `tags` / split members — **OR-Set** (add-wins). LWW on a JSON array means one device's concurrent add silently vanishes.
- Balances, budget rollups, net worth — **never synced.** Derived locally from transactions. Syncing a derived aggregate is how you get balances that disagree with the transactions beneath them.

**Deletes: soft, always.** `deleted_at` **plus `deleted_hlc`** — a boolean can't resolve delete-vs-update. Policy: **delete wins, but never silently.** Record a conflict so the UI can offer "restore — this was edited on another device after being deleted here." A zombie transaction quietly re-entering a balance is worse than a recoverable deletion.

**Outbox state machine** — on the *entry*, not the record, because a record can have one synced field and one pending field at once:

```
pending ──push──► syncing ──ok──► acked ──(GC after 30d)──► ⌫
   ▲                 │
   │                 ├──5xx/429/timeout──► failed ──backoff──► pending
   │                 └──attempts>8────────► poison ──► DLQ + surface to user
   └──── user re-edits same field (supersede: drop old entry) ────
```

- Push in `seq` order, **one batch in flight**. Concurrent batches break FIFO.
- Full jitter: `sleep = random(0, min(300s, 1s * 2^attempt))`. AWS measured this as best time-to-completion.
- Retry on network error/timeout/429/5xx. Never on 400/403/422 → poison. 401 → re-auth.
- Reset attempt counters on success, app foreground, connectivity regained.
- **Poison entries skip and continue** — head-of-line blocking on a FIFO outbox stalls everything. Safe because field application is order-independent. **Never silently drop; this is money.** Surface "couldn't sync N changes."
- Batch **500 events or 256 KB** per push. Gzip above ~1 KB — payloads repeat the 46-char HLC prefix and field names, so 5–10× is typical.

**Sync triggers** — events, not just timers: app foreground, local mutation (debounced ~1s), connectivity regained, manual pull-to-refresh.

**Cadence, honestly.** Design for foreground-triggered sync; treat background as a bonus.

| | Android | iOS |
|---|---|---|
| Mechanism | WorkManager 2.12.0 | BGTaskScheduler |
| Floor | 15 min (a floor, not a promise) | `earliestBeginDate` is a floor only |
| Reality | 1–4×/hour when healthy; **never on hostile OEMs** (Xiaomi, Huawei, Samsung, Oppo/Vivo/OnePlus — see dontkillmyapp.com) | a few times/day for a regularly-used app; **possibly never** for a rarely-used one |

**No foreground service.** Android 15 caps `dataSync` at ~6h/24h, requires per-type permissions, *and* a Play Console declaration. WorkManager avoids that entire review surface.

Tell users: *"syncs when you open the app, and usually in the background."* Anything stronger is a support burden. **The HLC design is what makes late sync harmless** — a week-old offline edit merges correctly — and that's the strongest argument for it over `updated_at`.

### 3.3 Auth

`drive.file` + `openid email profile`. **AppAuth/manual PKCE over Ktor, not the native sign-in SDKs.**

Counterintuitive but correct: Credential Manager and GoogleSignIn-iOS are built for *authentication* and return an ID token. We need an *authorization code* exchangeable for a refresh token carrying a Drive scope. Going native means two divergent SDK-specific flows for one OAuth transaction; PKCE is identical on both platforms and lives in `commonMain`.

| Layer | Implementation |
|---|---|
| Browser handoff | `expect/actual`: Android Custom Tabs; iOS `ASWebAuthenticationSession` (~40 lines each) |
| PKCE + token exchange | Ktor + kotlinx-serialization in `commonMain` |
| Token storage | iOS Keychain (`kSecAttrAccessibleAfterFirstUnlock`, so background sync can read it) / Android Keystore |
| Refresh | Ktor `Auth` plugin `bearer { refreshTokens { … } }`, single-flight |

- Label the button **"Connect Google Drive,"** never "Sign in." More honest, and it's what keeps Apple 4.8 from firing.
- **The app must be fully usable with no sign-in at all.** Natural for offline-first; also satisfies Apple's demo-account requirement (§7) and defeats 4.8.
- Google Picker integration for adopting an existing spreadsheet — `drive.file` cannot list or search files, so persist the `fileId` yourself.
- **Register both SHA-1s** in the Cloud console — your upload key *and* Google's Play App Signing certificate — or Sign-In works in debug and fails only in production.
- Refresh tokens can be revoked (user action, 6-month inactivity, password change). **Revoked credentials must never block local use.** The outbox keeps accumulating; show a quiet "Reconnect" affordance; re-auth drains the queue.
- In-app **"Disconnect Google"** calling `https://oauth2.googleapis.com/revoke` + wiping local tokens. Apple 5.1.1 requires this even without an app account.

---

## §4. Categorization

Three tiers as v1, with the pipeline made explicit:

```
raw text → normalize (NFKC, strip zero-width, collapse whitespace)
        → merchant extraction + normalization
        → Tier 1: learned rules (Rules tab, exact then fuzzy)
        → Tier 2: shipped seed keywords
        → Tier 3: ask user → write a new Tier 1 rule
```

- **Record which tier fired** in a `categorized_by` column. Tiller does this and it's the single best debugging affordance in the system.
- **Merchant normalization:** `TESCO STORES 3421 DUBLIN IE` → `Tesco`. Strip store numbers, city, country codes, `POS`, terminal IDs. When a user renames a merchant once, remember it — that map doubles as the categorization signal.
- Categorization applies to **your share** on splits, never the full paid amount.
- Rules match on text, not currency, so they work identically across EUR/INR/AED.

**Default taxonomy (lock this):** Groceries, Dining, Transport, Housing, Utilities, Health, Shopping, Entertainment, Travel, Family & Gifts, Subscriptions, Income, Transfer, Miscellaneous. Fourteen, currency-agnostic, assigned by purpose not merchant.

---

## §5. Gamification — rebuilt on the evidence

### 5.1 The organising principle

> **An expense tracker is a machine for delivering bad news about oneself.**

The **ostrich effect** (Karlsson, Loewenstein & Seppi 2009, *J. Risk & Uncertainty*; confirmed on brokerage login data by Sicherman et al., *RFS* 2016): people check their finances more when things are going well and **avoid looking when they're going badly** — precisely when the tool matters most. It compounds with the *what-the-hell effect* (Cochran & Tesser): violating a goal produces further abandonment, not correction. Both fire at the same moment: the blown budget.

**Every mechanic gets judged on one question: does this make opening the app on a bad day cheaper or more expensive?**

Corollary: **reward the act of looking; never punish what the looking reveals.**

### 5.2 Budget health that is fair under sparse logging

**Two channels, never blended.** A missing score is honest; a zero is an accusation.

```
coverage = days_with_at_least_one_entry / days_elapsed

w(d)        = 0.5 ** (age_in_days(d) / 7)          # 7-day half-life
adherence_e = Σ_d w(d)·on_track(e,d) / Σ_d w(d)     # observed days only
conf_e      = min(1, observed_days_e / 14)

health = 20 + 80 · ( Σ_e conf_e·adherence_e / Σ_e conf_e )
```

Rules:
- **`coverage < 0.40` → suppress the score entirely.** "Not enough to say yet." Never a low score, never a grey zero.
- **Non-logging can never decrease health.** Absence of an expense is not evidence of an expense.
- **Recency weighting** so a returning user reads as "recovering," not "failing."
- **Floor of 20.** Never render 3/100.
- **Baseline is the user's own trailing history** — never global norms, never other users.
- Envelopes below `conf_e` 0.3 show "still learning" rather than dragging the average.

**Windows: both, for different jobs.** Budget envelopes on the **calendar month** (rent, salary, statements are monthly). Health, mood and streaks on a **rolling 30 days** — a hard reset creates a cliff where a bad 28th destroys the month and leaves two dead days with nothing to play for.

### 5.3 Projected month-end spend

**Always a range, never a point. Never shown before day 7.**

1. **Naive linear** — `spend_to_date / days_elapsed × days_in_month`. Fallback only; wildly unstable early and biased high if rent landed on the 1st.
2. **Day-of-month weighted** (needs ~2–3 months history) — build the user's own cumulative curve `w(d)`, then `projection = spend_to_date / w(today)`. Big accuracy win, modest effort.
3. **Category-recurrence-aware** — the target:
   ```
   projection = fixed_recurring_remaining + variable_run_rate_remaining
   ```
   Detect recurrence by clustering on (normalized merchant, amount ±10%, interval ≈28–31d) with ≥2–3 observations. **Any already-paid recurring item leaves the run-rate base entirely** — this is the fix for "rent on the 1st makes me look doomed." Report a band from the user's own historical dispersion.

Presentation matters as much as the maths:
- ✅ "On this pace you'd finish around €1,180–€1,340 (budget €1,250)."
- ❌ "PROJECTED OVERSPEND €90" on the 3rd — a shame trigger built on a statistically meaningless extrapolation.

### 5.4 Mechanics that ship

| Mechanic | Parameters | Evidence |
|---|---|---|
| **Rolling count, not a streak** — "14 of the last 30 days" | no endowment to lose | captures the goal-gradient benefit without the loss-aversion cliff |
| If a streak counter ships at all | **weekly** ("5 of 7 days"), **2 grace days auto-absorbed**, regenerating 1 per 7 active days, cap 2, **granted free — never earned, never sold** | matches Duolingo's 2-freeze ceiling; selling repair monetizes anxiety you manufactured |
| **On a break: decay, never zero** | restore prior streak −25%; keep "longest" prominent | prevents reference-point reset → defuses what-the-hell |
| **Endowed progress** | new users start with **2 items already unlocked** and visible banked progress | Kivetz, Urminsky & Zheng 2006, *JMR*: a 12-stamp card pre-stamped with 2 beats a blank 10-stamp card |
| **≥2 goals always in flight** | overlapping milestones | counters the measured post-reward engagement trough |
| **Layered-SVG pet**, mood from count of healthy envelopes | ~25 hand-drawn pieces → 12 palettes × 6 patterns × 8 hats ≈ **576 looks**; all colour via CSS custom properties | currency-free by construction; asset cost is the binding constraint for a solo dev |
| **Unlock pacing** | 1 per 7–10 active days early, stretching to monthly by month 3; pool of 40–60; then seasonal palette rotations | tuned against a ~2–3 month novelty half-life |
| **Pick 1 of 3** at milestones | — | supports autonomy — the SDT need points and leaderboards measurably fail to satisfy — at zero extra asset cost |
| **Goal-labelled reminders** | **≤2/week**, gain-framed | Karlan et al.: goal-specific reminders 2× generic, +16% vs none. Loss framing bought nothing, so the anxiety is unpaid-for |

**Multi-currency:** the atomic scored unit is `Envelope(currency, category, period)`. Health and streaks measure *behaviour*, so they're currency-agnostic — combine only as "3 of 3 envelopes on track." **Never sum across currencies for a score; an FX move must never be able to break a streak.**

**Not rewarding logging over saving.** Two separate currencies: logging earns **coverage**, which is a *gate*, not a reward. Only outcomes earn unlocks. Reward **per-day-observed, not per-entry** — this kills the Fortune City failure mode, where the biggest reward was for *adding an expense* and reviewers reported "trying to spend more money to get new buildings," with a 5/day cap that made users stop logging once hit. **Nothing in the economy may be monotonically increasing in money spent.**

Baseline outcomes from the user's own history ("Groceries down 8% vs your 3-month median") are unfakeable in a *useful* direction: to fake it you must stop logging, which shows up as a coverage drop.

### 5.5 Do not build

| Never | Because |
|---|---|
| **Leaderboards, percentiles, "people like you"** | Card et al. 2012: below-median earners lost satisfaction and increased job-search intent; above-median unaffected. Asymmetric harm, negative EV — and it needed **no game layer**, just a percentile. In finance the gap isn't closeable by effort and the metric isn't valid across users (low spend may mean poverty). OSC 2024 found the leaderboard condition *reduced* activity. |
| **Streak resets to zero** | Loss scales with tenure → concentrates churn on your best users. **SEC names "streaks"** in its DEP enumeration. |
| **Points/badges for logging** | Engagement-contingent = **d = −0.40**, the worst cell in Deci et al. 1999. The OSC/BIT RCT (n=2,430) found points of *negligible economic value* increased trading ~40% — proof they move behaviour **without adding value**. |
| **Anything rewarding spend amount or count** | Fortune City. |
| **Confetti / celebration on transactions** | **Enjoined by name** in the Robinhood Massachusetts consent order ($7.5M, Jan 2024). |
| **Notification volume as an engagement lever** | Same consent order. |
| **Loot boxes, spin-to-win, mystery rewards** | Enjoined. The FCA found 1 in 27 trading-app users met the problem-gambling threshold — **worse than online gamblers at 1 in 29** — with harm concentrated in low-resilience, low-literacy users. The prize-linked-savings literature that supports randomized reward requires **real money** to work; a cosmetic loot box imports the mechanic without the payoff. |
| **HP/damage/decay; social accountability that damages others** | Habitica: punishment fires exactly when the user can least absorb it; users abandon rather than face penalties. |
| **Retrospective loss framing** ("€80 over") | Loss framing motivates only while the loss is still avoidable; after that it repels. |
| **A blended multi-currency score** | An FX move could break a streak. |
| **Anti-cheat warnings** | Insulting in a single-player tool, and an ostrich trigger. |
| **Any A/B test under a full quarter as evidence** | Under 4 weeks you measure novelty. Koivisto & Hamari found longer use → less enjoyment *and lower perceived usefulness of the underlying tool*; Hanus & Fox found gamified students ended with **lower** motivation and lower final exam scores, mediated by intrinsic motivation, non-significant at week 4 and negative after. |

### 5.6 Tone

| ❌ | ✅ |
|---|---|
| "You're €200 over your restaurant budget" | "Restaurants is at €450 of €250. Move the limit, or watch it?" |
| "You broke your 34-day streak" | "Your longest run is 34 days. Day 1 of the next one." |
| "You failed this month" | "This month ran higher than usual. Here's the one category that moved." |
| "You haven't logged in 9 days" | "Welcome back. Catch up 3 days, or start from today?" |
| "You're bad at saving" | "Groceries ran over three weeks in a row" |
| "Don't spend more than €X" | "€120 left this week" |
| Red as primary status colour | Amber/neutral for over-budget; red only for genuine emergencies |

Hard rules: never express disappointment. Never use "should." Missing data is described as *missing*, never as bad. Target the behaviour, never the person (Tangney's shame-vs-guilt distinction: guilt supports repair, shame produces withdrawal). Frame acquisitionally, not inhibitionally. The return path is one tap and never requires catching up.

### 5.7 Three tiers, chosen at onboarding, changeable in one tap

| Tier | Contents |
|---|---|
| **Off** | Plain tracker. No pet, no streak, no celebration. **Must be genuinely complete — not a punished mode.** |
| **Gentle** *(default)* | Pet, progress rings, rolling count with **no visible loss event ever**, one weekly digest |
| **Full** | Visible counters, milestones, unlock notifications |

Not a nicety. Gamification preferences are strongly heterogeneous (Hexad user types map onto Big Five traits); studies repeatedly find users split for and against the *same* element; Cleo gates its snark behind explicit consent even though snark is its brand; and YNAB's persistence proves gamification isn't required for retention in this category.

### 5.8 Where game state lives

**Local DB authoritative.** Snapshot periodically to a clearly-labelled `_AppState` tab as a **restore hint, not truth**.

**Everything must be reconstructible** — every game state derives from the ledger plus deterministic rules, so you can replay and recompute. This makes the snapshot optional and hand-edits **harmless by design**.

Two hard rules for replay, because the common hand-edit is a user adding forgotten receipts:
- **Unlocks are permanent and never revoked.**
- **Streaks recompute only in the user's favour** — backfilled days can save a streak, never break one.

Never store a reward balance as an authoritative mutable number the user can edit. Derive it.

**Anti-cheat: there isn't a problem, and say so out loud.** Single-player, no leaderboard to defend. The only person affected is the cheater, and the cheating is itself diagnostic information *they* can see. Note the reinforcing logic: shipping no social features (§5.5) is also what removes any integrity requirement. Where numbers must stay useful, use **silent devaluation** — never enforcement, never a warning.

---

## §6. Transaction capture

With notification scraping cut, **capture friction is the single biggest product risk.** Prototype these three before writing a line of sync engine.

| Mechanism | Platform | Friction | Notes |
|---|---|---|---|
| **Share sheet** | both | very low | Long-press a bank notification → Share → app. Android `ACTION_SEND` `text/plain`; iOS Share Extension. **The workhorse** — the user hands you the text deliberately, so it's fully compliant, and it's one gesture. |
| **CSV / OFX / QIF import** | both | medium, batched | **Essential.** Revolut, AIB, BOI, N26, Wise all export CSV; Indian banks export CSV/XLS. Highest data-volume path; how users backfill and reconcile. |
| **Widget** | both | very low | Tap → amount keypad. Android Glance; iOS WidgetKit + App Intents. |
| **Quick Settings tile** | Android | very low | One pull-down + tap. |
| **App Intents / Siri** | iOS | low | "Add €12.50 groceries." Also lets *users* author their own notification-triggered Shortcuts automations — their device, their choice. You expose an intent; you never read notifications. |
| **Clipboard quick-add** | both | low | On foreground, if the clipboard parses, offer a dismissible chip. Read only on explicit user action — otherwise it feels creepy. |
| Open banking | both | zero ongoing | v2, EUR only. GoCardless Bank Account Data (ex-Nordigen) is the best indie option for EU/Ireland. **India's RBI Account Aggregator framework effectively requires regulated-entity status — inaccessible to an indie dev.** So: aggregator for EUR, manual + import for INR. |

### 6.1 The parser

Same messy bank text, just handed over deliberately.

```
raw → normalize (NFKC, strip zero-width, collapse whitespace, unify symbols)
    → currency/locale detection (₹ / Rs. / Rs / INR → INR;  € / EUR → EUR)
    → versioned, remote-updatable rule pack (regex + named captures, per issuer)
    → candidates {amount, currency, direction, merchant, date, last4, ref}
    → confidence score
    → high → pre-filled confirm sheet (one tap)
      low  → review queue showing the raw text
```

**Ship rule packs server-updatable and versioned** — bank formats change without notice and you must not need an app release to fix a parse. With opt-in, log *unmatched* text (amounts redacted) to grow the pack.

Pitfalls to handle explicitly:
- **Decimal/grouping separators.** `1.234,56` (much of the EU) vs `1,234.56` (IE/UK/IN). `1.234` is 1234 in Germany, 1.234 in Ireland. **Never a locale-naive `toDouble()`.** Prefer the rightmost separator followed by exactly 2 digits as the decimal.
- **Indian lakh/crore grouping.** `1,23,456.78` — 2-digit groups break every Western thousands regex. Needs its own branch.
- **Symbol placement and variants.** `€12.50`, `12,50 €`, `EUR 12.50`, `₹500`, `Rs.500`, `Rs 500`, `INR 500`, `500/-` — sometimes `Rs` and `₹` in the same bank's messages.
- **Direction.** debit/credit/refund/reversal/sent/received/spent/"credited to"/"debited from"/"paid to". **The most-often-misparsed field, and getting it wrong flips the sign on money** — bias hard toward asking when confidence is low.
- **Authorization vs settlement double-count.** Card networks emit a pending auth then a settled txn, often with different amounts (tips, FX). Dedup on `(last4, amount ±tolerance, merchant_normalized, date ±3d)`, prefer settled. **Biggest source of duplicates.**
- **Truncation.** Shared text is often clipped mid-merchant. Treat merchant as low-confidence when the string ends in `…`; still capture the amount.
- **Localization.** Non-English bank messages; Devanagari; transliterated Hinglish. Don't assume ASCII.
- **UPI specifics.** VPA strings (`name@okhdfcbank`), `UPI Ref No`, and the fact that GPay/PhonePe/Paytm each phrase the same event differently.

Collect real example strings from your own EUR and INR accounts before writing the pack — don't trust any published list.

---

## §7. Store compliance

### 7.1 Hard blockers, designed out

1. **No notification/SMS parsing** (§0).
2. **`targetSdk 36`** from day one — required for new apps and all updates **from 31 Aug 2026** (extension to 1 Nov 2026 via Console).
3. **Never request `spreadsheets`, `drive`, or `drive.readonly`.** `spreadsheets` costs a 2–8 week sensitive review; `drive` costs months plus recurring paid CASA.
4. **`PrivacyInfo.xcprivacy`** — enforced at review since 1 May 2024. Your `NSUserDefaults` and disk-space usage will trigger required-reason declarations. Add it before the first TestFlight upload, not after a rejection.
5. **F-Droid is incompatible with Google Sign-In** — F-Droid prohibits Google Play Services/Firebase dependencies. Ship **Obtainium** (tracks GitHub Releases; zero friction — make this the day-one sideload answer), **IzzyOnDroid**, and **Accrescent** (notably does *not* restrict GMS) instead.

### 7.2 Apple

- **2.5.2** — CMP is fine; compiled native code, nothing downloaded or interpreted. Just don't ship remote config that changes *functionality*.
- **4.8** — Sign in with Apple **not required**, because the guideline turns on "primary account with the app" and Google here authorizes access to the *user's own Drive*. It also matches the exemption for "a client for a specific third-party service." Protect this: no app account, full functionality with no sign-in, and the button says "Connect Google Drive."
- **5.1.1(v)** — no account creation → no account-deletion requirement. But the same clause *does* require in-app credential revocation and forbids storing tokens off-device. Ship "Disconnect Google."
- **5.1.1(ix) / 3.2.1(viii)** — "banking and financial services" / "**money management**" should be submitted by the institution providing them. Low probability for a pure tracker, but the words are in the text. **Mitigate in metadata:** say "expense tracking," "spending log," "personal budgeting." Never "money management," "financial advice," "banking," "investment."
- **2.1** — the no-login local mode **is** the demo path. State this in Review Notes.
- **4.2 Minimum Functionality** — the most likely rejection for a lean tracker. **Launch with real depth**: recurring transactions, budgets, charts, CSV export, widgets. Not an MVP list view.
- **3.1.1** — nothing gated in v1. The moment cosmetics are sold it must be StoreKit IAP; a Sponsors link that unlocks anything in-app is a rejection. Donations with no unlock are fine.
- **App Privacy card** — with no server and no analytics you can honestly declare **"Data Not Collected."** Adding any crash reporter changes this. Age rating **4+**.

### 7.3 Google Play

- **Financial Services policy** — a tracker with no financial product is out of scope. Fill the declaration truthfully if prompted. Crypto tracking or "advice" would pull you in.
- **Data safety** — declare no collection (you neither collect nor transmit to yourself; Google's APIs are the *user's* destination). Must match Apple's card and the privacy policy. Any crash reporter changes it — decide before launch.
- **Data deletion** — apps without account creation are out of scope. Answer "does not offer account creation."
- **No foreground service** (§3.2). Avoids the FGS type declaration entirely.
- **No `QUERY_ALL_PACKAGES`.** If a dependency pulls it in, `tools:node="remove"`.
- **Closed testing gate — your critical path.** Personal accounts created after 13 Nov 2023 must run a closed test with **12 testers continuously opted in for ≥14 days** before applying for production access. Testers who opt out early don't count. **Start recruiting the moment you have a working build.**
- **Developer verification** — individual accounts publish your **legal name**; organization accounts publish a **legal address** and require a **D-U-N-S number (up to 30 days to issue)**. If you don't want your home address public, register an entity with a registered office and start the D-U-N-S request ~6 weeks out. Play Console fee: **$25 one-time**. Apple: **$99/yr**.
- **Watch item:** Google's sideloading developer verification (2026–27) may require registering package names and signing keys for GitHub-distributed APKs.

### 7.4 OAuth

`drive.file` + `openid email profile` = **all non-sensitive → no mandatory verification, no CASA, no 100-user cap, no 7-day refresh tokens, no demo video.**

Still needed: **brand verification** to show your name and logo on the consent screen — app homepage, privacy policy URL, and **domain ownership verified in Search Console**. So you need a real domain before publishing the consent screen (DNS 1–48h). Use a custom domain on GitHub Pages; it survives repo renames and satisfies both stores' privacy-policy hosting.

**OAuth client IDs in a public repo are fine** — Google documents installed-app clients as public, with the real control being binding to package name + signing SHA-1 (Android) or bundle ID (iOS). **Never commit** the upload keystore, keystore passwords, Play service-account JSON, App Store Connect `.p8`, or any web/server client secret.

---

## §8. Repository and release

### 8.1 Layout

```
expense-tracker/
├─ composeApp/                 # shared CMP UI + Android entry point
│  └─ src/{commonMain,androidMain,iosMain}/
├─ shared/
│  ├─ core-model/              # Money(minor units, currency), Transaction, Account, HLC
│  ├─ core-db/                 # SQLDelight schemas + migrations
│  ├─ core-sync/               # outbox, HLC, fold, conflict resolution
│  ├─ core-sheets/             # Sheets/Drive API client, batch builders, schema migration
│  ├─ core-auth/              # PKCE flow, token storage
│  ├─ feature-transactions/
│  ├─ feature-budgets/
│  ├─ feature-import/          # CSV/OFX/QIF + share-sheet parser + rule packs
│  └─ feature-gamification/
├─ iosApp/                     # Xcode project, direct integration
├─ build-logic/                # convention plugins (included build, NOT buildSrc)
├─ gradle/libs.versions.toml
├─ fastlane/
├─ .github/{workflows,ISSUE_TEMPLATE}/
├─ docs/                       # GitHub Pages: privacy policy, sheet schema
├─ sheets-template/            # the template spreadsheet + optional bound Apps Script
├─ LICENSE  NOTICE  TRADEMARK.md  CONTRIBUTING.md  SECURITY.md  CODE_OF_CONDUCT.md
```

Start with **one shared module** and split only when compile time or boundaries demand it (JetBrains' own advice). Use an **included build `build-logic/`**, not `buildSrc` — `buildSrc` invalidates the configuration cache on every change and doesn't inherit the version catalog. iOS via **direct integration** (`embedAndSignAppleFrameworkForXcode`).

Reference repos: **msasikanth/twine** (CMP, shipping on both stores + IzzyOnDroid — closest analogue) and **joreilly/Confetti** (exemplary `build-logic`).

### 8.2 Licensing

**Apache-2.0**, plus a registered trademark on the name/icon and a `TRADEMARK.md` forbidding use of the name in redistributed builds.

- **Avoid GPL/AGPL.** GPL §6's "no further restrictions" conflicts with Apple's Licensed Application EULA. VLC was pulled in Jan 2011 after a *copyright holder* complained and only returned in Jul 2013 after relicensing to LGPL. Apple doesn't screen for GPL — the risk materializes only when someone complains, and **once you accept outside contributions under GPL you can't relicense, and any single contributor can trigger it.**
- **Copyleft doesn't stop clones anyway** — a cloner complies by publishing their fork. What actually stops re-uploads is **trademark + store policy** (Apple 4.1 Copycats, 4.3 Spam, 5.2.1; Play Impersonation/Spam). Apache-2.0 §6 grants no trademark rights, which pairs cleanly.
- Apache-2.0 adds an express patent grant with defensive termination that MIT lacks. Requires root `LICENSE` + propagated `NOTICE`.
- **Trademark registration is a 6–12 month lead item — file before launch.** The license/CLA decision is one-way: settle it before the first external PR.

### 8.3 CI

**Keep macOS off the PR path.** Linux is $0.006/min; macOS is $0.062/min (dropped from $0.080 on 1 Jan 2026). **Public repos are free on standard runners** — a concrete reason to open-source from day one.

```
pull_request  → ubuntu: ktlint/detekt + commonTest + jvmTest + androidUnitTest   (~5 min)
              → ubuntu: assembleDebug                                            (~5 min)
push to main  → macos:  linkDebugFrameworkIosSimulatorArm64 + iosSimulatorArm64Test
tag v*        → macos:  fastlane gym + pilot → TestFlight
              → ubuntu: bundleRelease + fastlane supply → Play internal testing
```

~$15–25/month for two devs on a private repo; **free on a public one.** Cache `~/.konan` and `~/.gradle` aggressively — Kotlin/Native toolchain download plus linking dominates iOS build time.

Secrets hygiene: scope all publishing secrets to a **`production` GitHub Environment with required reviewers and a tag/branch restriction** — the highest-value single control. Secrets are not passed to fork-triggered workflows, not inherited by reusable workflows, and unreadable by Dependabot runs — so never put release secrets in a `pull_request`-triggered workflow, and never combine `pull_request_target` with a checkout of the PR head. Play publishing supports **Workload Identity Federation** (accepted by fastlane `supply`), eliminating the service-account JSON; Apple has no OIDC equivalent, so the `.p8` stays long-lived. `fastlane match` against a **private** certs repo, `--readonly` in CI.

**Testing:** the sync engine's correctness — HLC compare, LWW resolve, outbox state machine, amount parsing — is **pure `commonMain` logic**. Test it on Linux for pennies; that's where the real risk lives. Turbine for Flows, `runComposeUiTest` for shared UI, **Maestro** for E2E (drives the accessibility tree, one YAML for both platforms — set `Modifier.semantics { testTag }` deliberately or nothing is findable). Paparazzi/Roborazzi are Android/JVM-only, so accept no pixel-testing on iOS.

**Releases:** `release-please` with the `simple` strategy and `extra-files` pointing at `libs.versions.toml`. Deterministic `versionCode` from the semver triplet (`major*10000 + minor*100 + patch`) — not `github.run_number`, which resets if you rename the workflow. **AAB to Play; a universal APK signed with your own key to GitHub Releases** — never the AAB, which is uninstallable. Note this creates **two signature lineages: a GitHub APK is not update-compatible with a Play install.** Decide and label the policy before the first release; publish your cert's SHA-256 in the README.

---

## §9. Build order

**Phase 0 — long-lead, start immediately (nothing depends on code).**
Domain + Search Console verification · Apple Developer enrollment · Play Console account (+ D-U-N-S if incorporating) · trademark filing · privacy policy on GitHub Pages · Cloud project with `drive.file` client IDs (both SHA-1s).

**Phase 1 — spike the risk, in this order.**
1. **Capture friction prototype**: share-sheet target + CSV import + widget quick-add. Throwaway UI. *This is the biggest product risk; if it feels bad, the product doesn't work.*
2. **Amount-entry keypad** in CMP on a real iPhone. *Validates the one real CMP iOS risk.*
3. **Sheets write path**: `drive.file` → create spreadsheet → one `batchUpdate` → kill the app mid-write → verify no duplicate on retry.

**Phase 2 — the engine.** UUIDv7 + HLC + `Events` log + outbox + fold + soft deletes. Local DB authoritative. Heavy `commonMain` unit tests. This is the whole thing; get it right before building on it.

**Phase 3 — the app.** Transactions, accounts, multi-currency native display, transfers with locked rates, categorization tiers, budgets, charts, ledger, CSV export. Enough depth to clear Apple 4.2.

**Phase 4 — sheet surface.** Derived tabs, `Summary_YYYY`, dashboard over summaries, schema migration + archive flows, drift-diff UI.

**Phase 5 — gamification.** §5, Gentle tier only, off switch first.

**Phase 6 — ship.**
Play closed testing (**12 testers × 14 continuous days** — start this the moment Phase 3 builds) · TestFlight · privacy manifests + Data safety + Privacy card, all three consistent · review notes explaining the no-login demo path · store metadata scrubbed of "money management."

**Phase 7 — v2.** GoCardless for EUR · iOS App Intents · recurring-detection projection (§5.3 tier 3) · optional Apps Script digest via template copy.

---

## §10. Open questions

| Question | My recommendation |
|---|---|
| Multi-device day one? | **Yes** — the HLC/append-only design costs the same either way and retrofitting is painful. |
| Second spreadsheet at 100k rows, or archive tabs? | **Archive tabs first** (`Transactions_YYYY`), value-pasted. Split files only if someone actually gets there. |
| Individual or company Play account? | **Company** if you don't want your legal name public — but that means a D-U-N-S (30 days) and a public registered address. Decide before Phase 0. |
| Ship the pet in v1? | **Yes, but Gentle-tier only**, and build the Off switch first. It's the cheapest way to test whether the tone lands. |
| Encrypt the local DB? | **No in v1.** Platform at-rest encryption covers it; Keychain/Keystore for the refresh token. |
| Sell cosmetics later? | Only via StoreKit/Play Billing, and only after the free pool is exhausted. Never randomized. |

## §11. Things to verify before depending on them

- Exact current CMP/Kotlin versions and JetBrains' stability declaration at implementation time.
- Android 15/16 `dataSync` FGS limits verbatim (we avoid FGS, so this is informational).
- CMP iOS screenshot-testing story.
- Whether the "200 tabs" Sheets limit exists at all — it's absent from Google's docs; the 10M-cell budget is the real constraint.
- Real bank message samples from your own EUR and INR accounts, before writing rule packs.
- GitHub Actions macOS flat-rate vs the old 10× multiplier, against your actual billing page.
- Play's closed-testing gate still being in force at submission time.
