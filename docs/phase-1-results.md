# Phase 1 results — the three spikes

Phase 1 existed to answer three questions that could each have invalidated the
plan. All three are answered. The code written here is foundation, not product;
the findings are the actual output.

## Spike 1 — is manual capture fast enough? **Yes, logic proven**

Automatic notification capture was cut on policy grounds (see PLAN.md §0), so the
product now rests on manual paths. Both are implemented and tested.

- **Share-sheet parser** — 21 tests. EUR and INR, both separator conventions,
  Indian lakh grouping, `500/-`, UPI VPAs, truncated text, conflicting direction
  words.
- **CSV importer** — 13 tests. Revolut/AIB/HDFC shapes, separate debit and credit
  columns, semicolon delimiters, quoted fields, accounting negatives, preamble rows.

**Bugs the tests caught:**

| Bug | Why it mattered |
|---|---|
| `500/-` yielded no amount | No `₹`/`Rs`/`INR` present, so currency detection failed. The `/-` suffix is itself the marker in Indian usage. |
| `"Withdrawal Amt"` matched both the amount and debit synonym lists | The amount branch won, so every row read from the debit column and **every credit imported as nothing** — income silently vanishing, no error. |
| `"description"` contains `"cr"` | Description was detected as a credit column. Two-letter synonyms now match exactly, never as substrings. |

The second is the kind this phase exists to find: no crash, no error, just wrong
totals discovered weeks later.

**Still open:** the parser's confidence thresholds are judgement, not evidence.
Real message samples from actual EUR and INR accounts should replace the
plausible-shaped fixtures before the rule pack is trusted.

## Spike 2 — does Compose Multiplatform handle amount entry on iOS? **Yes**

Verified on an iPhone 13 Pro Max, not just the simulator. Confirmed good by hand.

The mitigation worked: a drawn numeric keypad means the system IME is never
summoned, so CMP's weakest iOS surface is bypassed entirely. It is also simply
better for the job — large targets, no letters, no autocorrect, and no way to
type a second decimal point. Entry is right-to-left like a card terminal, so
there is no decimal key at all.

**Bugs found:**

| Bug | Detail |
|---|---|
| Hard `SIGABRT` at launch | Compose's own `PlistSanityCheck` aborts unless `CADisableMinimumFrameDurationOnPhone` is `true`. No message names the key — it took reading Kotlin frames in the crash log. |
| Content drawn under the Dynamic Island | `.ignoresSafeArea(.all)` was wrong; only the keyboard inset should be ceded to Compose. |
| Compose 1.12 requires `compileSdk` 37 | While Play requires `targetSdk` 36. Different settings, both correct. |

**Build-system findings that changed the module layout** — AGP 8.x cannot run on
Gradle 9.6+, and AGP 9 cannot apply `com.android.application` alongside the KMP
plugin at all. So `:composeApp` is a KMP *library* holding the shared UI, with
`:androidApp` and `iosApp/` as thin shells. This is where AGP is heading anyway,
so it avoids a forced migration later — which was the reason for choosing this
stack over React Native. Full notes in [running-locally.md](running-locally.md).

## Spike 3 — can we write to Sheets without duplicating? **Yes**

13 tests against a fake sheet that models the real API honestly: atomic appends,
no upsert, no compare-and-swap, and the specific failure where the server commits
but the response is lost.

The plan's acceptance criterion — **ten consecutive crash-mid-write cycles, zero
duplicate rows** — passes. Also verified: fold convergence regardless of arrival
order, two devices editing different fields both winning, a week-offline device
merging in causal order, and supersede collapsing three corrections into one
event.

**Bug found:** `newEvents` deduplicated against known HLCs but not *within* a
batch, so an overlapping paginated read would double-apply.

**Not yet proven:** this is a faithful fake, not Google. The `drive.file` scope
claim and the locale trap both still need one run against a real spreadsheet.
That is the first task of Phase 2.

## Where things stand

74 tests, 0 failures. Android APK builds; iOS builds, signs, installs and runs on
a real device. Shared logic compiles to native on both platforms.

Nothing in the plan was invalidated. The riskiest assumption — that CMP could
handle the most-used interaction on iOS — held, with one non-obvious plist
requirement that would have cost a day to find later.
