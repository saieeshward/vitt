# Veteran pass plan — 2026-09-18

Produced by a seven-lens analysis (sync calls, local compute, refactoring, invariants, UX psychology, feature use, first run), each proposal challenged by a sceptic, then synthesised. 49 of the proposals survived.

# VITT — ordered plan from 49 proposals

Spot-checked before ordering: `VittApp.kt` is 871 lines with 20 `localRevision++`; `Syncer.kt:170-172` still reads from row 2; `Syncer.kt:228` catches only `BadRequest` inside bisect; `SyncController.kt` has no gate or Retry-After read; `VittServices.kt:51` is the UTC `today()`; `Transaction.kt:221` / `Transfer.kt:159` default day to 0; `LedgersScreen.kt:346` ships `"… over"`; `HabitScreen.kt:192,195` hardcode "Pip"; `VittApp.kt:522` is the `EUR, INR` fallback. The proposals' evidence holds.

## Dedupe

| Kept as one item | Folded in |
|---|---|
| Backoff + Retry-After gate in SyncController | #1, #20 (take #20's 300 s cap and `RateLimited(null) -> 60 s`; take #1's `Outbox.backoffMillis` for Transport/Server and the Unauthorized suspend) |
| Materialised-transactions memo | #7 step 1 now; #12 AppSnapshot later, built on the memo |
| Derived pending row for CONNECT_SHEET | #29, #39, #46 (one row, derived state, no snooze, no Choice) |
| "Nothing spent today" on the Add sheet | #33, #40 (identical placement: replaces the hint at `AddScreen.kt:351-355`) |
| CSV import door | #38, #48 (#48's date parser + fingerprint dedup, #38's two entry points) |
| Empty states carry their action | #42, #49 |
| Currency fallback | #43, #44 (`Currency.entries`, no locale, no Choice); #45 onboarding screen is the open decision |
| relativeDay | #15 alone, but it fixes a user-visible defect (`day 20701` in a message to a friend) so it rides with the invariants |

Conflict to resolve once: #22 makes `today()` device-local; #30 and #31 compute hours/days from HLC millis in UTC. All three must use the same `utcOffsetMillis` lambda. Do #22 first; #30/#31 consume it.

Dropped: #4 item (3) id_token email (nice, not load-bearing; do only if Settings gets a "Synced to" row), #31 endowed setup day (labelling setup as "a day looked" is the one item that flirts with §5.8 gaming; revisit after coverage data exists), #35 weekly digest (correct but needs notification permission plumbing on both shells; not before capture works), #26 as a standalone PR (bundle into the first Civil/Period touch).

## Dependency spine

```
WS1 Sync (shared only)            independent, start now
WS2 Invariants (shared only)      independent, start now
WS3 Write/read path + state holder  #9 -> #6 -> #7 -> #13 -> #12 -> #10 -> #8/#11
WS4 Capture loop (UI)             after #13 (no new localRevision++), after #23 (totals are right)
WS5 First run + data safety       after #12 (VittApp seams), after #22/#23/#24 (nudges and ledgers correct)
```

---

## WS1 — Sync correctness and call budget

**Goal.** `Syncer.sync()` never throws, the controller never hammers after a 429/401/offline, and a reconnect costs one read regardless of ledger size. Everything is `commonMain` under `runTest` virtual time.

Baseline (from the trace): idle cycle 1 call; one local edit 3; ten edits 3 s apart 30; iOS cold start 2 `files.get`; recovery after a dropped response `1 + ceil(rows/5000) + 1` reads; 429 storm N calls per N triggers; revoked grant 3 wasted calls per request.

Steps, in order:

1. **Bisect escape** (#19, hours, high). `Syncer.kt:195` wrap `bisect()` in `try/catch (probe: SheetsError)` returning `SyncOutcome(pushed=sent, poisoned, error=probe)`; batch stays IN_FLIGHT. `SyncController.runCycle` (`SyncController.kt:105-117`) gets `try { syncer().sync() } catch (CancellationException) { throw } catch (SheetsError) { SyncOutcome(error=e) } catch (Throwable) { … Transport }`. Comment at the probe lambda (`Syncer.kt:225-230`) saying why only BadRequest is caught there. Tests: `SyncerTest` "a connection lost during bisect leaves the batch in flight" (both variants: first probe throws; left half lands, right half throws) asserting sheet row count == acked count on the next cycle; `SyncControllerTest` "a syncer that throws still leaves Failed".
2. **Backoff gate** (#1+#20, day, medium). `SyncController`: `notBefore`, `consecutiveFailures`, injectable `random`. RateLimited -> `min(retryAfter ?: 60, 300) s`; Transport/Server -> `Outbox.backoffMillis(++failures, random)` (`Outbox.kt:138-142`, unused today); Unauthorized/FileNotAccessible -> suspend until `onDisconnected()` or the Off->connected transition; BadRequest -> no gate. `onForeground` returns early inside the gate; `onLocalWrite` defers with `delay(maxOf(debounceMillis, notBefore - now()))` and reuses the single `debounced` Job; `syncNow` bypasses and resets. `SyncStatus.Failed` gains `nextRetryAt: Long?`; `SheetSection.kt:138` renders "Retrying soon" / "Retrying in about a minute" (ranges, §5). Tests: the four `SyncControllerTest` cases in #1 plus `RateLimited(null)` = 60 s and `RateLimited(9999)` = 300 s, with `now` driven by the same test clock as `advanceTimeBy`.
3. **Fold recovery into the pull read** (#2, day, high). One private `ingest(id)` in `Syncer`; `recover` = ingest from `lastReadRow.coerceAtLeast(2)` then `reconcile(hlcs)`; `pull` = version check then `ingest`. Replace the `Syncer.kt:170-171` comment with the invariant and add it to `rememberReadRow` KDoc (`EventStore.kt:201-211`) with "assumes rows are never deleted". Tests: extend `SyncerTest.kt:137` to assert `reads == 1` and `lastFromRow == lastReadRow`; the foreign-device-appends-between test (this is the assertion that guards the invariant); confirm `SyncerTest.kt:302` still holds.
4. **Coalesce triggers** (#3, day, medium). `onForeground`: `if (gate.isLocked) return`; 30 s floor when `pendingCount()==0` (constructor param); `onLocalWrite`: `isConnected()` moved inside the job, `firstWriteAt` + 15 s cap. Fix the wrong comment at `MainViewController.kt:28-29`. Tests as listed in #3. Do this **after** WS3 step 1 (appendAll) lands or the per-event `onLocalWrite` count in the test fixture will be off.
5. **No bare-Bearer request** (#4 item 1, hours). `VittServices.kt:344` `accessToken: () -> String?`; `SheetsClient.auth()` (`:160-162`) throws `Unauthorized` on null before the request. Test: a fake token source returning null yields zero transport calls and `Unauthorized`.
6. **Atomic ledger create** (#5, hours, low). `SheetSpec.data` + minimal `GridData/RowData/CellData/ExtendedValue` in `SheetsWire.kt`; delete the header append at `SheetsTransport.kt:33-37`. Test: serialised JSON shape; add the A1:F1 read-back step to `LiveVerification.kt:51-56` and run it against the real API once before merging.

**Measure.** Re-run the trace table after each step with the fake transport's call counters. Targets: idle 1 (unchanged); recovery on 50k rows `11+ -> 1`; ten edits 3 s apart `30 -> 6`; iOS cold start `2 -> 1`; 429 storm `N -> 1` per window; revoked grant `3 -> 0` per request.

**Do not.** Do not add a `wanted` re-run loop to `onForeground` (a cycle always ends with pull). Do not change `journal_mode`. Do not touch scopes or the client ID (constraints 2, 3). Do not build a `BisectOutcome` type; the try/catch is enough.

---

## WS2 — Ledger invariants (one predicate, one calendar day, no phantom rows)

**Goal.** Every total, nudge and chart is derived from the same three rules, and the rules are pinned in `:shared:jvmTest` so WS4/WS5 build on figures that agree to the cent.

1. **`counts as money` predicate** (#23, hours, high). `Transaction.movesMoney / isSpend / isIncome` with KDoc "sign is the authority for direction; category only decides whether the row counts". Replace raw filters at `LedgerRepository.kt:561-567`, `Insights.kt:92-97, 373-377`, `Nudge.kt:92`, `ActivityScreen.kt:114`; `LedgerRepository.kt:123` filters on sign before `mapNotNull`. Tests: extend the `InsightsTest` fixture with `transfer` rows in each currency; assert `byCategory.sum == ledgers.spent`, `trend.spent == ledgers.spent`, no SET_BUDGET for a transfer-only currency. Post-change grep: `amount.isOutflow|isInflow` survivors are only `MoneyLine.kt:53`, `ActivityScreen.kt:203`, `Account.kt:166`, `Nudge.kt:83`.
2. **Reject no-day / zero rows** (#24, hours, medium). `Transaction.from` (`:211-221`) and `Transfer.from` (`:159`) return null. Tests beside `LedgerRepositoryTest.kt:157`: no FIELD_DAY -> empty and `activityDayRange()==null`; amount 0 -> `daysRecorded()==0`.
3. **Budget natural key** (#25, hours, low). `Budget.from` requires `key.entityId` to be the canonical code and to agree with the currency field. Tests in `BudgetTest.kt` including the 3-permutation convergence check.
4. **Device-local today** (#22, hours, high). `VittServices(utcOffsetMillis: (Long) -> Long = { 0L })`, `today() = floorDiv(now() + offset(now()), 86_400_000)`, `millisUntilNextLocalMidnight()`; shells pass the lambda (`MainActivity.kt:68`, `MainViewController.kt:15`); fix `Civil.kt:13-14` comment; `AppRoot.kt:54-55` becomes `produceState` ticking at local midnight and re-read on foreground. Tests: +5:30 at 23:30Z -> day 1; -5:00 at 02:00Z -> day 0; offset 0 leaves every existing test green. **Any later HLC-hour or HLC-day derivation (#30, #31) must use this lambda, not `% 24` on UTC millis.**
5. **relativeDay + SettlementSummary date** (#15, hours). `PeriodLabels.relativeDay/longDay`; delete `ActivityScreen.formatDay`, the `HabitScreen.kt:140-145` and `VittApp.kt:242-248` variants; `SettlementSummary.kt:38` default becomes an absolute date with year. `PeriodLabelsTest` property: result never matches `day \d+` over 0..800. Fix the ` — ` separator at `SettlementSummary.kt:64` to `, ` in the same pass (design-identity: no em dashes).
6. **Period laws** (#26) ride along with whichever step first touches `Civil.kt`; do not open a separate PR.

**Measure.** `./gradlew :shared:allTests` green with the new fixtures (em dashes in test names). Manual: Reports category total equals the ledger card on a ledger containing a `transfer` row; a 04:00 IST entry lands on the local day.

**Do not.** No migration of already-written UTC days. No date-window bound on `day`. Do not put `check()` invariants in `budgets()`; the projection enforces the key.

---

## WS3 — Write path, read path, and the VittApp state holder

**Goal.** One SQLite commit per logical write, sub-millisecond fold per edit, one `transactions()` materialisation per revision, and the read side of `VittApp` testable under jvmTest. This is the prerequisite for every UI restructure in WS4/WS5.

Baseline (JVM, 1,581 txns): fold after one write 37.6 ms; root repository bundle 6.5 ms warm; 10-15 `transactions()` per revision plus 2 per People participant; 6-8 commits per recorded transaction; every tap recomposes `VittApp`.

1. **`EventStore.appendAll`** (#9, day). One `transactionWithResult`, per-event insert + `changes()` + enqueue, `rememberClock` once with max HLC, `touched()` once per entity, `onLocalWrite()` once. `append()` delegates. Switch the loops at `LedgerRepository.kt:100, 272, 449, 502, 521, 590, 661` and `categorise` (`:404-440`); chunk CSV import at 500. Tests: `EventStoreTest` appendAll cases; counting `SqlDriver` wrapper asserting `record()` opens exactly one transaction; `RecordScalingTest` transaction count for 1,000 rows. Unblocks WS1 step 4.
2. **Incremental fold** (#6, day, high). `applied(event)` in the three append paths updates a retained winners map and rebuilds one `EntityKey`; refactor `EventLog.fold` into `winners()` + `materialise()`; `appendSuperseding` returns `inserted`. Test: jvmTest on the StressTest corpus interleaving local/remote/shuffled/tombstone/duplicate writes, asserting `foldOf == EventLog.fold(selectByEntity)` and zero SQL via the counting driver. With appendAll in place, the map publishes once per batch, not per event.
3. **Materialised-transactions memo** (#7 step 1, hours). `txCache: Pair<Long, List<Transaction>>?` keyed on `store.versionOf(Transaction.ENTITY)`. Zero API change. Test: counting wrapper asserts 1 materialisation across `ledgers()+byDay()+owed()` at one version, 2 after an append. Then `VittApp.kt:325-328` People lambdas become remembered maps.
4. **Revision as a StateFlow** (#13 step 1, hours). `LedgerRepository.revision: StateFlow<Int>` bumped in `appendAll`; `VittApp.kt:141` collects it; delete all 20 `localRevision++`. Step 2 (choice rules into `Preferences`: LAST_ACCOUNT compare-before-write, companion None-as-value, home encoding) with `PreferenceTest` cases. Absorb #16's `CompanionHome`/`HomeLayout` codecs here, since they are the same three rules; pass `theme`/`accent` down from `AppRoot` to kill the double decode. **Everything in WS4 that adds an action must land after this so no new `localRevision++` is written.**
5. **`AppSnapshot` in :shared** (#12, days). Revision-only slice with `transactions` exposed once; `days` and `nudge` stay outside as pure functions over the snapshot. Absorb #14 (sealed `Nudge`, no `!!`) here since the snapshot owns the nudge mapping; add the NudgeTest assertion. `AppSnapshotTest` covers mood, currencyIndex, companion home parse, nudge snooze skip. Target `VittApp.kt` 871 -> ~450 lines.
6. **Hoist Reports/Add/People inputs** (#10, hours). `ReportsData.build` lazy, keyed on `(revision, currency, period, today)`; `frequentCategories` returns both lists in one pass; People derives from one `openSplits()`. Test: Insights evaluation counter shows 0 on a Ledgers->Reports->Ledgers->Reports round trip at unchanged revision (was 7).
7. **Recomposition tuning** (#8 + #11, hours, only after 3-6). `compose-stability.conf`, `items(key = { it.id })` in `ActivityScreen.kt:136` and `LedgersScreen.kt:145`, `currencyIndex` as a remembered value-equal map, pointer counter as `State<Int>` into `CompanionLayer`. Verify with compose metrics (`-PcomposeMetrics`) and Layout Inspector: 1 `TransactionRow` per note edit, 0 root recompositions per tap.
8. Hygiene while in the files: #17 `CurrencyChips` (three chip rows -> one), #21 `PeriodNav` with `PeriodNavTest`, #18 delete `SpikeApp.kt` (zero callers; record in `vitt.clan`).

**Measure.** JVM probe before/after: fold per edit 37.6 ms -> <1 ms; root bundle 6.5 -> ~2.5 ms; commits per `record()` 6-8 -> 1 (device timer around `record()` and a 1,000-row import); `grep -c 'localRevision++'` 20 -> 0; `grep -c '!!' VittApp.kt` 2 -> 0; recomposition counts from the metrics report.

**Do not.** No `produceState`/off-main-thread folding yet (separate, measured change). No `LedgerSnapshot` class in the repository; the memo covers it. No `VittActions` interface, no `Route` type in shared. No generic `ChoiceKey<T>`. Do not touch WAL/journal settings.

---

## WS4 — Capture loop: make the features that exist reachable, and give Add an end

**Goal.** Merchant, split participants, no-spend marking, categoriser, drilldown and settlement text become reachable from the daily path; every Add ends on a designed note; the one §5.5-listed string is gone. All items are hours to a day; this is the highest impact-per-effort cluster in the set.

Order (after WS3 step 4 and WS2 step 1):

1. **Split opens its participant sheet** (#37, hours, high). `VittApp.kt:531-551`: capture `id` before `record()`, `sheet = if (new.totalPaid != null) Sheet.Split(id) else null`; `CategorySheet` gains `onOpenSplit` for already-saved splits. Test: commonTest documenting `openSplits()` contains a participant-less split while `openSplitParticipants()` does not.
2. **Merchant on manual entries** (#36, day, high). `NewEntry.merchant`, `Step.Where` cloned from `Step.Note`, third chip in the row at `AddScreen.kt:328-345`, `LedgerRepository.recentMerchants(today, 90, 6)`. Leave `category` null so `record()` categorises and `categorySource` is honest. Tests: `recentMerchants` dedupe/window/order; `record()` receives merchant from the UI path.
3. **"Nothing spent today" on Add** (#33+#40, hours). Replaces the hint at `AddScreen.kt:351-355` only when `step == Main && entry.isEmpty && onNothingToday != null`; one `markToday` lambda shared with `HabitScreen`; gated on `habitOn` explicitly.
4. **Progressive disclosure** (#32, hours). Move the Expense/Income row below the keypad next to categories; `showHint = transactions.size < 3`. Pin `frequentCategories` limit 6 with the Hick comment.
5. **Receipt + acknowledgement** (#27, half day). One `inkMuted` line above the tab bar, 1.8 s, identical for every entry; `acknowledgeTick` on `CompanionPet` plays one LOOK_UP only on the first record of the day; reduce-motion branch unchanged. Copy is past tense, no adjective, no exclamation.
6. **Over-budget line** (#28, hours). `ledgerCardLine(...)` pure function in `:shared` with `LedgerCardLineTest` asserting no "over", no minus, contains "Move the limit?"; state-aware `onClickLabel`. `grep '" over"' composeApp` -> 0.
7. **RECORD_TODAY at the user's hour** (#30, day). `typicalRecordHour(today, 30)` median over first-per-entity amount events, using the WS2 offset lambda (not UTC); gate `nowHour >= typical - 1`; null = no gate. Tests: evening-logger fixture, single import day = one sample, <5 days = no gate.
8. **Android share-sheet capture** (#34, staged: Android ACTION_SEND first, one day). `FIELD_SOURCE` on Transaction, `Sheet.Add(prefill)`, `Prefill` from `AmountParser` only when `confidence != LOW`, else raw text into the note (§6.1 "bias toward asking"). Ship and measure the share of `source=share` entries before any iOS App Intent or widget work.

**Measure.** Fresh install: `categorySource != MANUAL` count goes from a hard 0 to >0 after the first merchant-tagged entry; repeat entry for a known merchant = amount + Where chip + Save. Split participant naming: unreachable -> 0 extra taps. No-spend mark 2 taps via unlabelled icon -> 1. Taps from bank notification to saved entry ~6 -> 3 on Android. RECORD_TODAY tap-through vs expiry (`onNudgeTap` vs `onNudgeExpired`) as a local counter.

**Do not.** No hop, particles or sound on Save (Robinhood line). No preselected category chip from the categoriser (keeps the CategorySheet promise at `:184` true). No undo event for a mis-tapped no-spend mark. No iOS Share Extension before the Android numbers exist (it needs the App Group signing step). No weekly digest yet.

---

## WS5 — First run and data safety, without an onboarding wall

**Goal.** A GBP/USD/AED/JPY user can log their own currency on first open, a two-currency user reaches the second currency from Add, every empty screen names its exit, and the "your data is on one phone" fact reaches 100% of users including the Off tier.

1. **Currency fallback** (#43+#44, hours, high). `VittApp.kt:522` -> `(ledgers.map { it.currency } + Currency.entries).distinct()`; drop the `size > 1` gate at `AddScreen.kt:266` (or add a "More" chip if six chips crowd 375 pt: measure first); `AddScreen.kt:92` initial currency `initialAccount?.currency ?: currencies.first()`, delete the `?: Currency.EUR`. Reports at `VittApp.kt:339-343`: when `ledgers.isEmpty()` render "Reports start with the first entry." and skip `ReportsSheet` (no euro hero). Test: shown-currency ordering helper in shared (account, seen, rest, no duplicates).
2. **Empty states carry their action; kill "Pip"** (#42+#49, hours). `LedgersScreen.onAdd`, "Log something" button; Accounts body "Optional. Add one to keep a balance. Entries work without one." plus "Open an account"; Activity "Nothing recorded yet. Entries land here, newest first."; `HabitScreen.kt:192,195` and `SettingsSheet.kt:145` interpolate `animal?.label`. Acceptance: `grep -rn 'Pip' composeApp/.../ui/screens/` -> 0, `grep 'middle button'` -> 0.
3. **Derived pending row on Ledgers** (#29+#39+#46, day, high). Compute `Nudges.pending` once; companion keeps `firstOrNull { !snoozed }`; `LedgersScreen` gets `pendingRows` = CONNECT_SHEET and REVIEW_CATEGORIES only, rendered under OwedRow regardless of `habitOn`/companion, labels are facts, hidden when empty, no snooze, no Choice, removed by the connected state itself (§5.8). Make OwedRow clickable. Test: `Nudges.pending(habitOn=false, connected=false, firstDay=today-7)` contains CONNECT_SHEET.
4. **Settings order** (#41, hours). `SheetSection` first when not Off; Home toggle only when `currencyCount > 1`; export row under "Your data".
5. **CSV import door** (#38+#48, days). Shared first: `CsvDate.parse`, `CsvRow.day`, fingerprint dedup `(day, minor, currency, normalised description)`; `rememberFileImporter` expect/actual beside `FileExport.kt`; `Sheet.ImportReview` with "Skipped 12 already recorded"; entry points on the Ledgers empty state and Settings "Your data". Merchants flow into the WS4 categoriser.
6. **Adopt an existing ledger by pasted link** (#47, week, high). `Syncer.adoptLedger(id)` validates the tab set, resets `KEY_DRIVE_VERSION` and the read cursor, then `sync()`; `SpreadsheetLink.parse`; quiet "Use a sheet VITT already made" under Connect. No Picker, no scope change.

**Measure.** Currencies reachable at first run 2 -> 6; second currency 4-tap detour -> 1 tap; CONNECT_SHEET reach = 100% of day-7 users (was the habit-on-and-pet subset); euro hero for non-euro users 0; duplicate "VITT ledger" files per reinstall -> 0 for users who take the adopt branch.

**Do not.** No locale read (expat risk, and `Currency.entries` makes it unnecessary). No `Choice.CURRENCIES` unless the onboarding screen is approved. No dead links to CSV import or adoption before those screens exist. No "silent done" onboarding write for existing installs (it puts a row in every connected sheet). Constraints 1-3 untouched throughout.

---

## This week

1. **WS1 step 1, bisect escape** (`Syncer.kt:195`, `SyncController.kt:105-117`, two `SyncerTest` cases + one `SyncControllerTest`). Hours; it is the only item that leaves a user with a permanent spinner and a batch nobody retries correctly.
2. **WS2 steps 1-2, movesMoney predicate + reject no-day/zero rows** (`Transaction.kt`, `Insights.kt:373-377`, `LedgerRepository.kt:561-567`, `Nudge.kt:92`, `InsightsTest` fixture). Hours; every later screen inherits correct totals and no 1970 month.
3. **WS5 step 1 + WS4 step 1, currency fallback and split-opens-participants** (`VittApp.kt:522, 531-551`, `AddScreen.kt:92, 266`). Hours each; they remove the two places where the product's premise (multi-currency, splits) is unreachable on a fresh install. Write them with `localRevision++` for now only if WS3 step 4 has not landed; otherwise land step 4 first (it is also hours).

Then WS3 steps 1-3 (appendAll, incremental fold, memo) as one performance PR with the JVM probe numbers in the description.

## The one decision for the maintainer

**Does §5.7 "tier chosen at onboarding" require a first-run screen (#45), or is default-on with a one-tap Settings switch an acceptable reading?** Everything in WS5 branches on it: with a screen, `Choice.CURRENCIES`/`ONBOARDING` exist, the Add sheet reads chosen currencies, and the companion Yes/No is asked once; without it, WS5 steps 1-3 as written are the complete first-run story and no Choice keys are added. My recommendation is no screen: the currency set is derivable from what the user logs, the Off tier is one tap away, and every screen before the first entry loses users (Apple 4.8 risk is also lower with nothing that resembles a sign-in wall). But it reverses a PLAN.md line, so it is the maintainer's call, recorded via `clan patch-decision`.

## Surviving proposals

- [medium/day] sync-calls: Call budget today (baseline table) and backoff that honours Retry-After in SyncController
- [high/day] sync-calls: Fold the recovery read into the pull read and start it at lastReadRow, not row 2
- [medium/day] sync-calls: Coalesce foreground and edit triggers: no double cold start, join a running cycle, adaptive debounce
- [low/hours] sync-calls: OAuth path: no userinfo call exists (good); stop the guaranteed 401 round trip when signed out mid-cycle and fill email from id_token for free
- [low/hours] sync-calls: Create the ledger and its header in one spreadsheets.create call
- [high/day] local-compute: Incremental fold: apply only new events to the cached entity map instead of re-reading the whole type on every write
- [medium/days] local-compute: One LedgerSnapshot per revision: materialise transactions once, not 10 to 15 times
- [medium/hours] local-compute: Declare :shared model types stable and key the lazy lists, so an edit recomposes one row instead of every row
- [medium/day] local-compute: Commit one logical write as one SQLite transaction (EventStore.appendAll)
- [medium/hours] local-compute: Stop recomputing Reports and Add-sheet inputs on every tab switch and sheet open
- [low/hours] local-compute: Take the pointer-interaction counter out of the root composable
- [medium/days] refactor: Pull the read side of VittApp into a testable AppSnapshot in :shared
- [medium/day] refactor: Give VittApp an actions interface and make writes bump revision in one place
- [low/hours] refactor: Type the nudge destination: one Route per NudgeKind, no `currency!!`
- [medium/hours] refactor: One relativeDay() in time/PeriodLabels; stop leaking `day 20345` into settlement messages
- [low/day] refactor: Typed Choice keys with codecs; move CompanionHome parse/encode out of the composable
- [low/hours] refactor: Extract CurrencyChips and stop threading `currencyIndex` lambdas through four screens
- [low/hours] refactor: Delete SpikeApp.kt; keep VerifyScreen behind the launch flag
- [high/hours] refactor: Sync: an error thrown inside bisect escapes sync() and leaves the status stuck on Syncing
- [medium/hours] refactor: Sync: honour RateLimited.retryAfterSeconds instead of retrying on the next debounce
- [low/hours] refactor: Bundle the period navigation props into one PeriodNav value shared by Ledgers and Activity
- [high/hours] logic: Compute 'today' as the device-local date, not the UTC day
- [high/hours] logic: One predicate for 'counts as money' so TRANSFER-labelled rows are excluded everywhere, not in two places
- [medium/hours] logic: Reject a projected Transaction/Transfer with no day or a zero amount instead of defaulting to epoch day 0
- [low/hours] logic: Budget.from must require the currency field to match its natural key
- [low/hours] logic: Pin the period arithmetic with a property test rather than examples; the code is right today but has no guard against regression
- [medium/day] ux-psychology: Give the Add flow an end: a one-line receipt and a Pip acknowledgement on Save
- [medium/hours] ux-psychology: Replace the "€89.64 over" loss line with a forward choice: "of €1,800 · Move the limit?"
- [high/day] ux-psychology: Nudges must survive the companion being off: a quiet Pending line on Ledgers for the Off tier
- [medium/day] ux-psychology: Fire RECORD_TODAY at the user's own logging hour, not at first open
- [low/hours] ux-psychology: Endow the first day: setup counts as a day looked, so Habit never opens on zero
- [medium/hours] ux-psychology: Progressive disclosure on Add: retire the standing hint after three saves and demote Income below the keypad
- [medium/hours] ux-psychology: Put "Nothing spent today" one tap from home, as a soft commitment that discharges the open day
- [high/week] ux-psychology: Build the Fogg prompt at the moment of paying: share-sheet capture and a keypad widget before any more reporting
- [medium/days] ux-psychology: Ship the Gentle-tier weekly digest as an opt-in local notification, gain-framed, and nothing else
- [high/day] feature-use: Manual entries carry a merchant, so learning, drilldown and rules stop being dead code
- [high/hours] feature-use: Close the split dead-end: a split opens its participant sheet the moment it is saved
- [high/days] feature-use: Give CSV import a door: Reports 'Your data' and the empty Ledgers screen
- [medium/day] feature-use: A pending list on Ledgers that the pet points at, so nudges survive Habit off and Companion none
- [medium/hours] feature-use: 'Nothing spent today' where the RECORD_TODAY nudge actually sends people
- [medium/hours] feature-use: Settings: sheet status to the top, irrelevant Home toggle hidden, one 'Your data' row
- [medium/hours] feature-use: Empty states carry their one action: Ledgers 'Log something', Accounts 'Open an account'
- [high/hours] first-run: First-run trace: what a fresh install shows today, and the two functional holes it exposes
- [medium/day] first-run: Persist the user's currency set as a Choice and derive it from device locale, replacing the listOf(EUR, INR) fallback
- [high/days] first-run: A three-step onboarding state machine, persisted as Choice keys, ending in the Add sheet
- [medium/day] first-run: Offer the Google Drive backup once, right after the first saved entry, and stop gating the connect nudge behind the companion
- [high/week] first-run: Restore path: 'I already use VITT' adopts an existing sheet through the Google Picker instead of silently creating a second one
- [high/days] first-run: CSV import as an onboarding branch: the parser exists, the file picker and review screen do not
- [medium/hours] first-run: Retune the empty-state copy: name the way out, stop implying a required step, and stop calling every animal Pip