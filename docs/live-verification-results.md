# Live verification — run against real Google

Six checks, executed on a physical iPhone against a real Google account and a
real spreadsheet created in the user's Drive.

Everything before this was tested against `FakeSheet`, a double written from
documentation. A double can only confirm that the code matches its author's
understanding of the API — these runs are the first evidence about Google itself.

## Result: 6 of 6 pass (2026-08-26, run 3)

| Check | Result |
|---|---|
| `drive.file` permits creating a spreadsheet | **PASS** |
| `drive.file` permits appending | **PASS** — 3 rows |
| `RAW` survives a locale round trip | **PASS** — `1.234`, `03/04/2026`, `=1+1`, `1,23,456.78` all returned verbatim |
| Read back and fold reproduces local state | **PASS** — 3 events, `txn-1` amount = −1250 |
| Duplicate appends really do occur | **PASS** — no upsert, so dedupe must stay client-side |
| Drive `version` changes on write | **PASS** — `2 → 2 → 3` |

**The load-bearing claim is confirmed.** `drive.file` is non-sensitive *and*
sufficient for create, append and read on app-created files. No CASA assessment,
no annual reassessment, no 100-user cap. This now rests on evidence rather than
on a reading of the documentation.

The locale result matters nearly as much: `=1+1` came back as text rather than
becoming a formula, and `1.234` was not reinterpreted as one thousand two hundred
and thirty four. `RAW` plus a pinned spreadsheet locale defuses the silent
date/decimal corruption described in PLAN §2.4.

## The one behaviour to design around

**Drive's `version` counter lags by seconds.** Observed `2 → 2 → 3`: unchanged
immediately after a write, incremented about four seconds later.

That is fine for its actual job — noticing edits the *user* made in the
spreadsheet — but the sync loop must never read "version unchanged right after I
wrote" as "my write did not land". Write confirmation comes from the append
response and from reconciling by HLC, never from this counter.

## How this was nearly recorded as a Drive limitation

Worth keeping, because the failure mode is easy to repeat.

- **Run 1** reported `before=2 after=2` and was written up here as a finding that
  *disproved* PLAN §2.5's change-detection design.
- **Run 2**, with a delay and `modifiedTime` alongside, reported `version 3→3→3`
  and `modifiedTime` **identical to the millisecond** across a four-second gap —
  verdict "neither moved, needs `changes.list`".

That millisecond-identical timestamp is the tell. A genuinely lagging counter
still returns a fresh timestamp; only a cached HTTP response returns a
byte-identical one. Ktor's Darwin engine uses `NSURLSession`, which **caches GET
responses by default**, so the second poll was answered from the first one's
response.

The fix was two headers on the poll (`SheetsClient.fileVersion`), not a redesign.
Acting on run 1 would have meant building a `changes.list` page-token sync path
around a bug in our own HTTP layer — and leaving that bug in place, where it
would have broken change detection in production while looking like a platform
limitation.

**The lesson for the harness: measure the mechanism, do not just assert the
outcome.** Run 2 failed too, but it printed the evidence that identified the real
cause.

## What these runs did not test

- Behaviour at quota (60 writes/min/user)
- Concurrent edits from the Sheets web UI during a sync
- A spreadsheet with existing hand-edited or malformed rows
- Token refresh mid-sync, or a revoked grant
- Anything at scale — four rows, not fifty thousand

## Running it again

Auto-run is wired to a launch environment variable so the checks can be executed
and captured without tapping a button on a device:

```bash
xcrun devicectl device process launch --device <udid> \
  --terminate-existing --console -e '{"VITT_AUTORUN":"1"}' ie.shoonya.vitt
```

Results print as `VITT-CHECK <PASS|FAIL> | <check> | <detail>`.

Each run leaves a real spreadsheet in the user's Drive. That is deliberate — the
point is to be able to open it and see what actually landed.
