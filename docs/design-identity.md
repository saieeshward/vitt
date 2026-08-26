# Design identity

**Source: the Claude Design project "Design identity and mockups plan"**
(`3508bb23-2d08-4f90-8ade-17e537e66a5a`), imported 2026-08-26. That document is
the authority; this file records what was implemented and where.

> The identity was authored under the earlier name **Tender**. The product is now
> **VITT**; the design carries over unchanged, because none of it depends on the
> name. The mark and the piggy-bank companion are name-independent.

> **Why this is a document and not a Figma handoff.** The UI is Compose
> Multiplatform — Kotlin drawing to a Skia canvas. Tokens are Kotlin values that
> compile into the app, so there is no step where a design becomes code.

---

## The idea the whole identity serves

> **The refusal to convert is the product**, so the identity has to say
> *parallel*, never *combined*.

Every competitor's premise is one net-worth number. One number needs a rate, a
rate is always slightly stale, and a stale rate makes net worth swing on days you
spent nothing. VITT declines the estimate, so it can never be wrong about it.

**A currency is a colour.** Hues are assigned in order from a fixed six-hue set,
so EUR is the same green for every user, and adding a fourth currency does not
recolour the first three.

## Colour roles

Money is **never coloured by sentiment**. Spending is plain text. Income and
settled debts take the soft accent. The accent marks only what is *live* or
*mine* — never what is *good*.

| Role | Light | Dark |
|---|---|---|
| Ground | `#FFFDF7` cream | `#17141F` |
| Surface | `#F2EFE7` | `#241F33` |
| Ink | `#241F33` | `#DAD5E6` |
| Accent — live, mine, now | `#6B5BFF` | `#9C8DFF` |
| Pip | `#EE6E9C` | `#D95C88` |

Currency hues, in assignment order: green `#35B98A`, amber `#E39A12`, blue
`#4C9DF7`, pink `#EE6E9C`, violet `#6B5BFF`, teal `#17A2A2` — lifted for dark.

**Health runs accent → neutral, never green → red.** A red ledger is a verdict,
and the tone rules forbid verdicts; over budget is the *absence* of accent, so a
bad month goes quiet rather than shouting. `VittColors.health(pressure)`
implements this, and there is deliberately no `error` role for money.

**The single red in the product is a destructive confirmation.** It is named
`destructive` so that reaching for it to paint a balance reads wrong in review.

**Chroma lives in lines, dots and glows** — never a filled bar or a coloured
card. That is what keeps the palette working at four or five currencies.

## The money line

- **Tabular numerals everywhere.** Not cosmetic: proportional digits make a
  column of amounts ragged, and make the keypad display shift sideways as each
  digit is typed — the most-used screen in the app.
- **A figure never appears without its own currency symbol.** There is no default
  currency, so a bare number is always ambiguous.
- **No converted equivalent, ever.** `MoneyLine` has no parameter to pass one.
- **On a split, your share is the large figure**, with what you actually paid
  demoted beneath — because your share is what the budget and categories see.
- **Transfers are the one place two currencies touch.** The rate used is a
  recorded fact, shown as an arrow, never applied to anything else.

## Type

One rule: **one loud number per screen.** `moneyHero` at 34sp is the only large
size; everything else sits at 13–15sp. Hierarchy is size and space, not weight —
there is no bold body style to reach for.

## Voice

Receipts, not coaching. Past tense, no exclamation marks, no adjectives about the
user's choices. The app reports what it did and where it put it — *"Logged to row
412."*

## Companion — Pip

A piggy bank with **one coin slot per currency**. Coins go in through their own
slot and never move between them, which makes the app's one hard rule visible
without a word of explanation. Pip reacts only to *whether you logged*, never to
how much you spent. Colour deepens with the streak.

## Information architecture

Five tabs; the middle one is a button, because logging is not a destination.

| | | |
|---|---|---|
| 1 | **Ledgers** | one card per currency; accounts below, grouped, unsummed |
| 2 | **Activity** | every transaction, day-grouped; drafts on top |
| – | **Add** | a sheet, not a tab — one thumb reach from everywhere |
| 3 | **Ledger** | people and what is owed, per currency |
| 4 | **Habit** | the one place gamification is loud |

Reports and Settings live under Ledgers' header icons: monthly visits should not
slow the daily path.

---

## Implemented so far

| Piece | File |
|---|---|
| Palette, currency hues | `ui/theme/Palette.kt` |
| Semantic roles, `health()`, `currency()` | `ui/theme/VittColors.kt` |
| Type scale, spacing, `VittTheme` | `ui/theme/VittTheme.kt` |
| `MoneyLine`, `SplitMoneyLine` | `ui/MoneyLine.kt` |
| `Money.display()` — symbol + grouping | `shared/…/money/Money.kt` |
| `AmountKeypad` retuned to tokens | `ui/AmountKeypad.kt` |

**Not yet built:** the ten mockup screens, the Pip illustration, the mark, and
the tab bar. Screens are specified in the source document.

## Where this supersedes an earlier draft

An earlier version of this file proposed warm neutrals with **amber for
over-budget**. The imported identity is stronger and replaces it: health runs
accent-to-neutral so a bad month goes quiet rather than amber, and colour carries
*which currency* rather than *how you are doing*. The earlier draft's reasoning
about the ostrich effect survives — it just reaches a better answer.
