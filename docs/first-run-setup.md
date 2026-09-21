# First run: setting up accounts

The one screen shown before the app on a cold install. Designed and built
2026-09-21, after walking the previous first run in the code.

Lives in `ui/screens/SetupScreen.kt`, branched from `VittApp` on
`Choice.SETUP_DONE`. The chip lists are `money/AccountSuggestions.kt` in
`:shared`, so both platforms and the tests read the same names.

## The problem it solves

Opening VITT cold used to show `LedgersScreen.kt`'s "Nothing recorded yet. Tap
the middle button to log something." So you did, and the entry landed with
`accountId = null`, because you have no accounts. Nothing told you accounts
existed.

The Accounts section sits *below* the currency cards on the same tab
(`AccountsScreen.kt`), and on an empty app it reads "No accounts yet." You have
to scroll past your own new entry to find it.

So the concept the product is built on gets discovered by accident, and the first
handful of entries are orphaned until someone goes back and reassigns them. That
is a sequencing problem, not a knowledge problem, which is why the fix is a setup
step and not an explanation.

## Why not a walkthrough

A "Welcome to VITT" carousel would fight the pitch. The store listing says no
account, open it and start; four slides of value propositions is the thing people
tap through without reading. It also cannot do the work, because the work is a
decision, not a fact. Nobody forgets that a spending app tracks spending. What
they do not know is that this one wants their accounts named first.

Everything a carousel would have said belongs where it is acted on instead. Two
lines of that already exist and are good: "Grouped by currency, never added up."
and "Moving your own money is not spending, so no budget counts it."

## The screen

One screen, before the tab bar, shown once. Heading: **"Where does your money
sit?"** Subhead: "Name them however you think of them. You can change any of this
later."

Below that, a list you build in place. Not the existing `AccountSheet` repeated:
that is a good sheet for one account, but it is a two-step flow with name,
currency, kind and opening balance, roughly six taps each. Nobody does that five
times before seeing any value.

### Suggestion chips carry the whole flow

A currency selector across the top, defaulting to the device region's currency.
Under it, tappable chips of common account names for that currency. One tap adds
the account with that name and moves on.

Each currency's own names come first, then the same generic four appended to
every currency: **Current, Savings, Card, Cash**.

| Currency | Named chips |
|---|---|
| EUR | AIB, Bank of Ireland, Revolut, N26 |
| INR | HDFC, ICICI, SBI, Axis, Paytm |
| GBP | Monzo, Starling, Barclays, HSBC |
| USD | Chase, Bank of America, Wells Fargo |
| AED | Emirates NBD, ADCB, Mashreq |
| JPY | MUFG, SMBC, Japan Post |

This is the single biggest lever in the design: five accounts in about twenty
seconds of tapping instead of three minutes of typing. A free-text row stays
underneath for anything not listed, and the chips are only ever a shortcut to the
same free text, so nothing is locked to a provider.

Added accounts appear as a growing list above the chips, each with a Remove
beside it. A chip already used is shown selected and disabled, so the list and
the chips never disagree about what has been added. Nothing is written to the
event log until Start, so removing a row costs nothing.

### What is deliberately not asked

**Opening balance.** The highest-friction field on the sheet. It needs the
keypad, and it often needs the person to go and look it up in another app. It
blocks nothing: an account with no opening balance is a correct account whose
balance is relative rather than absolute. Ask per account later, at the point
someone looks at a balance and wants it to be the real one.

**Kind.** Default everything to `CURRENT`. Kind decides whether a negative
balance reads as "owed" or "overdrawn" and nothing else, so it is small print on
the card, and asking for it up front gives it a weight it does not carry. Credit
is the only kind that changes the wording, and that is a correction, which the
edit sheet now handles.

Both are reachable the moment setup ends, because tapping an account row opens
the edit sheet.

### Skipping

"Skip" is a plain button in the header, not hidden. A skipped setup leaves the
current empty state exactly as it is today, and the Accounts section keeps its
"No accounts yet" copy. Nothing about the app depends on setup having been run.

The screen shows once. The flag is a `Choice`, so it syncs like every other
preference and a second device does not ask again.

### Ending

Setup ends into the Add sheet, not into an empty Ledgers screen. Someone who just
named five accounts is one tap from their first real entry, and that entry now
has somewhere to land. The account picker on it defaults to the first account
they added.

## Where it lands in code

- `Choice.SETUP_DONE`, read in `VittApp` rather than `AppRoot`, so that finishing
  can set `sheet = Sheet.Add` on the way out.
- `SetupScreen.kt` in `ui/screens/`, calling the existing
  `LedgerRepository.openAccount` once per added account.
- `AccountSuggestions` in `:shared`, beside `Currency`, so both platforms and the
  tests read the same names.
- `deviceCurrency()`, an `expect`/`actual` over `NSLocale` and `java.util.Locale`.
  It returns null rather than guessing when the region names a currency the app
  does not carry.
- Nothing new in the ledger model. `openAccount` already defaults the kind and
  the opening balance, which is exactly what this screen wants.
- Both seeders in `VittServices` now set `SETUP_DONE`, or a screenshot run would
  open on this screen instead of on the app.

## Questions that were open, and how they were settled

1. **Bank name lists.** Shipped, with the generic set appended to every currency
   rather than offered as an alternative to the names. `TRADEMARK.md` turned out
   not to bear on this at all: it governs VITT's own name against forks, and says
   nothing about third parties. Using a bank's name to refer to that bank is what
   the name is for, the chip only fills a free-text field the user owns and can
   edit immediately, and nothing is stored but the string they accepted.

   The real risk was never trademark, it was staleness and coverage: a hardcoded
   list dates, and it is empty for anyone outside the six currencies the app
   knows. Appending Current, Savings, Card and Cash to every currency fixes both.
   A currency with no named banks still has four chips instead of a bare
   keyboard, and a bank that disappears costs one typed name. A test holds the
   floor: every currency offers something, and the generic four are always
   reachable.

2. **Currency default.** The device region, via `deviceCurrency()`, falling back
   to EUR when the region names a currency the app does not carry. That is a
   guess about the *first* account and nothing more, which is why the currency
   chips sit above the list and stay live: the second currency is the interesting
   one and it cannot be inferred. It is also the only place in the app that reads
   the region, and it never touches a figure.

3. **Google connect in the first-run path.** No, and this one was never close.
   Connecting Drive is optional and always has been. Putting an optional sign-in
   in front of a person on launch would make the app feel like it wants an
   account after all, which is the one impression the whole product is arranged
   to avoid.

## Still worth doing

- Editable opening balance, so the figure someone skipped at setup can be filled
  in later. The field is on the event log already; it needs a repository mutator
  and a row in the edit sheet.
- A prompt for the second currency after the first account is added. Today the
  chips are there and live, but nothing draws attention to them.
