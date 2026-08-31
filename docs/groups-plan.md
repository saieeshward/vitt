# Groups — design plan

A group is several people sharing expenses over time: a flat, a trip, a household
spanning two countries. Splitwise's territory.

Nothing in `PLAN.md` covers this. It is greenfield, and it is not a small feature —
so this document exists to settle the shape before any of it is built.

The short version: **most of it falls out of machinery that already exists**, and
the storage question is answered and verified. The FX question turns out to be less
of a collision than expected — Splitwise already keeps currencies separate, so the
hard call is validated rather than novel. What remains distinctive is narrower: the
*rate* a settlement records. See below.

## What already answers itself

| Question | Answer | Why it is already settled |
|---|---|---|
| Identity | The signed-in Google email | `openid email profile` is already requested. No app-side account, so §0.1 holds. |
| Storage | One spreadsheet per group, shared with member emails | `permissions.create` accepts `drive.file` — verified against the REST method reference. Stays non-sensitive: no CASA, no 100-user cap. |
| Multi-writer convergence | The existing HLC + append-only log + per-field LWW | This is exactly what Phase 2 was built for. A co-member is just a device that belongs to someone else. |
| Membership set | A map of LWW-registers keyed by member email | The project's existing CRDT. See below — this is *not* the OR-Set that `PLAN.md:221` prescribes for tags. |
| Notifications | There are none | FCM, APNs and Drive `changes.watch` all need a server. §0 forbids one. Members see changes on next poll. |

## The privacy line, which is not negotiable

The group sheet holds **only group expenses**. The personal ledger stays in the
user's own private sheet and is never shared.

This is a requirement, not a preference. Drive sharing is per file, and a `writer`
on your main sheet reads your entire financial life. Splitting a dinner must not
expose a salary.

So a member syncs two surfaces: their private sheet, and one sheet per group.

## The sheet is transport, not the record

Drive files have exactly one owner. If the founder deletes the file or walks away,
naively the group dies with it.

It does not, and the reason is structural: every member's app holds the complete
event log locally, because the local DB is authoritative (§0.3). Any member can
re-materialise the group into a new spreadsheet from their own replica.

This is worth stating out loud in the UI. A group is not hosted anywhere. It is a
log that several people happen to hold copies of, and the spreadsheet is a
convenient place for them to hand each other new entries.

## Membership is per-member LWW, not an OR-Set

`PLAN.md:221` prescribes an OR-Set for tags and split members, and it is right
there: add-wins, because LWW on a JSON array silently drops a concurrent add.

Membership is a different shape, because **members get removed**, and add-wins
means removal always loses. Model it instead as the CRDT the project already has:

```
entity   = "group_member"
entityId = the member's lowercased email      ← natural key
fields   = { name, active, joined_day }
```

A natural key rather than a UUID, so two devices adding the same person converge on
one member instead of creating two. Lowercased, because Google addresses are
case-insensitive and a case difference would fork the identity.

Consequence worth accepting deliberately: this puts an email address in an entity
id, and therefore in the shared sheet. That is inherent — the group's purpose is to
know who is in it.

Removal follows the project's soft-delete rule (§"deletes: soft, always"): set
`active` false, keep the row, so a concurrent edit from a device that had not yet
learned about the removal can still be resolved rather than lost.

## Splitting an expense

An expense records the payer, the total, and **resolved shares per member**.

**Store resolved amounts, never the splitting rule.** A rule ("equal, among
everyone") plus a later membership change would silently re-split history — last
month's dinner would quietly acquire a new participant. Resolve at entry, store
integers.

**The remainder problem.** €10 three ways is 333/333/334 minor units. Shares must
sum to exactly the total, with no floating point anywhere (hard constraint), and
every device must compute the *same* assignment without coordinating.

Largest-remainder, with a deterministic tiebreak on member id. Same inputs, same
answer on every device, sums exact by construction. Test the boundary: totals
indivisible by member count, and the 1-minor-unit total split four ways.

Split modes — equal, exact, weighted, percentage — are entry affordances. All four
resolve to integer minor units before anything is written.

## Balances: per currency — which is table stakes, not the differentiator

I assumed Splitwise blends currencies into one net number. **It does not**, and
correcting that changes the strategy.

| App | Multi-currency behaviour | Rate used |
|---|---|---|
| **Splitwise** (free) | Keeps balances **separate per currency** by default — *"You owe $12.45 and you are owed £8.72."* | none |
| **Splitwise Pro** | One-click conversion of a whole friendship or group | **current** market rate |
| **Tricount** | Converts every expense into the group's default currency automatically | market / daily rate |
| **Revolut** | Group Bills; settles in any supported currency over real payment rails | its own FX |

So per-currency balances are **not a differentiator**. They match Splitwise's free
tier. Building them is necessary and unremarkable — and claiming them as a selling
point would be a claim a reviewer could falsify in one minute.

**What is genuinely different is the rate.** Every competitor that converts uses a
*market* rate: Splitwise Pro at today's rate, Tricount at the rate on entry day.
None records the rate the money actually moved at. Convert a six-month-old trip in
Splitwise Pro and the amount owed shifts because the market shifted — the exact
failure §0.6 exists to prevent.

Ours is the only one where a cross-currency settlement records **the rate that
actually happened**, taken from the two legs of a real transfer. Narrower than "we
handle multi-currency properly", and defensible.

**Splitwise's own reasoning is worth keeping on file**, because it is independent
support for §0.6 from the market leader:

> there may not be a fair solution to the currency conversion problem that is
> generally acceptable, so they'll continue to mostly keep currencies separate

When someone eventually argues for a convenience conversion feature, that is the
counter-argument, and it is not ours.

**Balances are derived, never written.** `PLAN.md:222` is explicit that syncing a
derived aggregate is how balances come to disagree with the transactions beneath
them. Fold them locally from the log, every time.

## What the competitors mean for scope

**Revolut is not a competitor we can meet, and should not try.** Group Bills
settles over real payment rails, instantly, in-app. That is a bank feature and it
needs a bank. Its weakness is that the smooth path requires everyone on Revolut —
and it tracks money it can see, so it is not a tracker for accounts held elsewhere.

**Tricount is the closest product** and its choice is the opposite of ours: it
optimises for a single tidy number at the end of a trip. That is genuinely nicer
for a one-week holiday, and genuinely wrong for someone whose life spans two
currencies permanently. Different user, not a worse app.

**Splitwise has already validated the hard call** and put conversion behind a
paywall, which suggests users ask for it constantly. Expect the same pressure.

The honest positioning: not "better group splitting" — Splitwise free already does
per-currency balances and has network effects we will never have. It is that groups
sit inside a **multi-currency tracker with no account, no server, and the user's own
sheet as storage**. Groups are a feature of that, not a Splitwise competitor.

That argues for keeping the group feature deliberately small. See phasing.

## Settlement is a Transfer, which we were already building

Settling a cross-currency group debt needs a rate. The rate is whatever the payment
actually moved at — which is precisely the `Transfer` type, locking in the
historical rate observed from its two legs.

That is a real unification, not a coincidence: a settlement *is* money moving
between two accounts at an observed rate. Build `Transfer` first and settlement
mostly falls out.

## Debt simplification: opt-in, and a view only

Splitwise's "simplify debts" reduces the payment graph. It is also its most
complained-about feature, because it invents debts between people who never
transacted with each other.

If built: **off by default**, simplified **per currency independently**, and a
*view* that is never written to the log — same rule as balances.

## The trust model, stated honestly

Every member is a Drive `writer`, so every member can append anything to the group
sheet, including edits to someone else's expense. Per-field LWW means the last
writer wins.

This is adequate for friends and family and inadequate for adversarial parties.
Do not pretend otherwise in the UI. Two things make it liveable:

- **Attribution is free.** Every event carries an HLC bearing the originating node
  id, so the log already shows who changed what and when.
- **A client-side rule** that you edit only your own expenses. That is UX, not
  security, and should be described as such.

Drive permissions are the real access control. Within the group, the log is the
audit trail.

## Phasing

Each stage ships value alone, and the risky one sits behind a verification.

| Stage | What | Depends on |
|---|---|---|
| **G0** | Local-only groups. Named members, split shares, per-currency balances — entirely in the user's own sheet, nothing shared. | Nothing new. Generalises the one-sided split. |
| **G1** | `mailto:` settlement summaries. The user sends; the app only composes. | G0 |
| **G2** | Shared group sheet. Real convergence between members. | The two-account Drive run below |
| **G3** | Settlement recorded as a `Transfer` with its locked rate. | `Transfer`, G2 |

**G0 earns its place independently.** Most of the value of a group — knowing who
owes what after a trip — needs no sharing at all, because one person usually does
the recording. Ship it in Phase 3. G2 belongs in v2 (§7), beside GoCardless.

**G1 is not throwaway.** Since there are no push notifications, the email handoff
stays the only channel that reliably reaches a co-member, before and after G2.

## Before depending on any of this

Per §11 — these are unproven, and doc-reading will not close them:

- **A non-owner picking a shared app-created sheet gets a durable `drive.file`
  grant.** The scope definition covers files "that the user shares with an app
  while using the Google Picker API", and ACLs persist until revoked — but no
  document states the composition. Needs one run with two real Google accounts.
  **This gates G2 entirely.**
- **Whether a Drive `writer` can trash a file they do not own.** If yes, any member
  can break the group's transport. Recoverable — see "transport, not the record" —
  but it changes what the UI must warn about.
- **Google Picker in Compose Multiplatform on iOS.** Its own API key and setup, and
  no existing spike covers it.
- **Store surface.** Groups store third parties' email addresses. Play Data Safety
  and the privacy policy must both declare it. The position is clean and worth
  stating plainly: those addresses live in the user's own Drive and never touch a
  server we run, because we run none. Under GDPR the user is the controller and the
  app is not even a processor.

## Sources

Competitor behaviour above was checked on 2026-08-31, not recalled:

- [Splitwise — managing a friendship or group with multiple currencies](https://kb.splitwise.com/balances-and-expenses/how-can-i-manage-a-friendship-or-group-with-multiple-currencies)
- [Splitwise — can Splitwise do currency conversion?](https://feedback.splitwise.com/knowledgebase/articles/301146-can-splitwise-do-currency-conversion-between-multi)
- [Splitwise — improve the way Splitwise handles multi-currency](https://feedback.splitwise.com/forums/162446-general/suggestions/6765166-improve-the-way-splitwise-handles-multi-currency)
- [Tricount — multi-currency support for travellers](https://tricount.com/en-us/expense-tracker-features/multi-currency-support)
- [Revolut — how to use Group Bills](https://help.revolut.com/en-HU/help/app-features/splitting-expenses-with-group-bills/)

`permissions.create` scope requirement:

- [Drive API — permissions.create reference](https://developers.google.com/workspace/drive/api/reference/rest/v3/permissions/create)
- [Drive API — choose scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
