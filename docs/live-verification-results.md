# Live verification — run against real Google, 2026-08-26

Six checks, on a physical iPhone, against a real Google account and a real
spreadsheet created in the user's Drive. **Five passed, one failed**, and the
failure invalidates part of the plan.

Everything before this was tested against `FakeSheet`, a double written from
documentation. A double can only confirm the code matches its author's
understanding of the API — this run is the first evidence about Google itself.

## Confirmed

| Check | Result |
|---|---|
| **`drive.file` permits creating a spreadsheet** | PASS |
| **`drive.file` permits appending** | PASS — 3 rows |
| **`RAW` survives a locale round trip** | PASS — `1.234`, `03/04/2026`, `=1+1`, `1,23,456.78` all returned verbatim |
| **Read back and fold reproduces local state** | PASS — 3 events, `txn-1` amount = −1250 |
| **Duplicate appends really do occur** | PASS — no upsert, so dedupe must be client-side |

The first two are the load-bearing ones. **The whole no-verification strategy
now rests on evidence rather than on a documentation reading**: `drive.file` is
non-sensitive, and it is sufficient. No CASA assessment, no annual
reassessment, no 100-user cap.

The locale result matters nearly as much. `=1+1` came back as text rather than
as a formula, and `1.234` was not reinterpreted as one thousand two hundred and
thirty four. `RAW` plus a pinned spreadsheet locale defuses the silent
date/decimal corruption described in PLAN §2.4.

## Failed — and it changes the design

**`drive version changes on write` — `before=2 after=2`.**

A row was appended between the two reads and Drive's `version` counter did not
move. PLAN §2.5 makes this counter the entire change-detection mechanism:

> Poll `drive.files.get(fileId, fields="version,modifiedTime")` — a monotonic
> counter that "reflects every change made to the file on the server." Cheap;
> false positives, never false negatives.

**"Never false negatives" is now disproven.** A design that polls `version` to
notice edits will miss edits.

Candidate explanations, none yet distinguished:

1. `version` is eventually consistent, and the second read raced the write.
2. `version` tracks Drive-level metadata (rename, move, share) rather than
   content changes made through the Sheets API.
3. Sheets batches its version increments over a short window.

**What must happen before this is designed around again:** re-run with a delay
between write and re-read, and compare `modifiedTime` alongside `version`. If
`version` is merely lagging, a poll interval longer than the lag is sufficient.
If it does not track Sheets-API content edits at all, the fallback is Drive's
`changes.list` feed with a persisted page token — more work, and it needs its
own live verification.

Until that is settled, **the app can detect its own writes but cannot reliably
notice a user's hand-edits** — which is a real gap, because the product invites
exactly that editing.

## What this run did not test

- Behaviour at quota (60 writes/min/user)
- Concurrent edits from the Sheets web UI during a sync
- A spreadsheet with existing hand-edited or malformed rows
- Token refresh mid-sync, or a revoked grant
- Anything at scale — this was 4 rows, not 50,000

## Residue

The run leaves a real spreadsheet in the user's Drive
(`19OVn7CqVwm2dX2OqEwrTLdZUfz0GFCSI-WNzg5P5BqM`). Deliberate: the point is to be
able to open it and see what actually landed.

Note that `LiveVerification` writes a one-cell `probe` row into the Events tab,
which is precisely the malformed row that would break a strict reader. That is a
known defect in the harness, tracked as M24 in the failure-mode analysis.
