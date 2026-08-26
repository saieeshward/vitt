# Design identity and mockup plan

Design decisions for VITT, derived from the constraints already fixed in
[PLAN.md](../PLAN.md) rather than invented alongside them.

> **Why this is a document and not a Figma file.** The UI is Compose
> Multiplatform — Kotlin drawing to a Skia canvas — so there is no HTML, no CSS
> and no React component to design against. Tokens here are Kotlin values that
> compile into the app, and mockups are built as real Composables. There is no
> handoff step where a design becomes code, because the design *is* the code.

---

## 1. The organising principle

Everything below follows from one finding (PLAN.md §5.1):

> **An expense tracker is a machine for delivering bad news about oneself**, and
> people avoid checking their finances precisely when things are going badly —
> the ostrich effect.

So the visual test for every screen is: **does this make opening the app on a bad
day cheaper or more expensive?** A design that looks sharp on a good month and
accusatory on a bad one has failed, because the bad month is when the tool
matters.

Three consequences that override normal dashboard convention:

1. **No red for over-budget.** Red is reserved for genuine emergencies —
   sync failure, data at risk. Over-budget is **amber**, which reads as
   information rather than alarm.
2. **Absence is never styled as failure.** A missing day is grey and quiet, never
   a gap in a chart or a broken bar.
3. **The number is never the loudest thing on screen when it is bad.** Emphasis
   goes to the action available, not the damage done.

---

## 2. Palette

Two neutral ramps and three semantic accents. Deliberately small: every colour
has to justify itself, and a large palette invites decorative use of colour that
then reads as meaning.

| Token | Light | Dark | Used for |
|---|---|---|---|
| `surface` | `#FCFBFA` | `#141413` | page background |
| `surfaceRaised` | `#FFFFFF` | `#1E1E1C` | cards, sheets |
| `surfaceSunken` | `#F2F0ED` | `#0D0D0C` | keypad keys, wells |
| `ink` | `#1A1917` | `#F5F3F0` | primary text, amounts |
| `inkMuted` | `#6B6862` | `#A3A099` | secondary text, dates |
| `inkFaint` | `#A8A49D` | `#6B6862` | absent data, placeholders |
| `accent` | `#3A6B5C` | `#5E9C88` | primary action, healthy state |
| `caution` | `#B0742A` | `#D89A4E` | over budget, needs attention |
| `alarm` | `#A33B32` | `#D4675C` | sync failure, data at risk **only** |

**Warm-neutral, not blue-grey.** Finance apps default to cool blues, which read
as institutional. VITT is a personal tool, and warm neutrals sit closer to paper
than to a banking portal.

**The accent is a muted green, not a saturated one.** A bright green reward
colour turns every healthy state into a small celebration, which is the pattern
that got Robinhood enjoined (PLAN.md §5.5). Muted green is legible as "fine"
without being congratulatory.

**Contrast floor: 4.5:1 for text, 3:1 for meaningful non-text.** `inkFaint` on
`surface` is used only where the *absence* of contrast carries the meaning —
placeholder amounts, unlogged days — never for information the user must read.

**Colour is never the only signal.** Over-budget carries an icon and a label as
well as amber; roughly 8% of men have a colour-vision deficiency and a currency
figure is exactly the wrong place to rely on hue.

---

## 3. Typography

| Role | Size / weight | Notes |
|---|---|---|
| `amountHero` | 48 / Medium, tabular | the keypad display |
| `amountRow` | 17 / Medium, tabular | list rows |
| `title` | 20 / Semibold | screen titles |
| `body` | 16 / Regular | general |
| `label` | 13 / Medium | field labels, chips |
| `caption` | 12 / Regular | dates, provenance |

**Tabular figures everywhere a number appears.** Proportional digits make a
column of amounts ragged and, worse, make a changing amount jitter as digits are
typed. This is a one-line setting that gets missed constantly.

**System font, both platforms.** SF on iOS, Roboto on Android. A custom face
would cost bundle size, add a licence obligation, and — because CMP draws its own
text — is the surface most likely to expose rendering differences between
platforms.

**Currency symbols never abbreviate.** `€` and `₹` render at full size, not
superscripted. A shrunken symbol next to a large number is where multi-currency
apps start to feel careless.

---

## 4. The one rule the whole product exists for

**Amounts are never blended across currencies, anywhere, at any size.**

Visually this means:

- Balances stack, never sum: `€1,240.50` above `₹84,300`, each with its own row.
- No single "total net worth" figure exists to design. If a layout needs one to
  look complete, the layout is wrong.
- A per-currency section header carries the currency; individual rows do not
  repeat it, which keeps rows scannable.
- Indian grouping is 2-2-3 (`₹1,23,456.78`), already implemented in
  `AmountEntry.display()`. Western grouping applied to a rupee figure looks
  subtly wrong to anyone who uses rupees.

---

## 5. Component inventory

Built and shipping:

| Component | Status | Notes |
|---|---|---|
| `AmountKeypad` | **done, device-verified** | right-to-left entry, no system IME, no decimal key |

Planned, in build order:

| Component | Purpose | Design notes |
|---|---|---|
| `TransactionRow` | ledger line | merchant, category, amount; amount right-aligned tabular; provenance icon when imported rather than typed |
| `CurrencySection` | groups rows | sticky header carrying the currency; the mechanism that enforces §4 |
| `HealthRing` | budget health | **two channels, never merged**: fill = health, stroke style = confidence. Dotted stroke under 40% coverage, with "Not enough to say yet" — never a low score |
| `EnvelopeBar` | per-category budget | amber past 100%, never red; shows remaining, not overage, while remaining is positive |
| `ProjectionBand` | month-end estimate | a *range*, never a point; suppressed before day 7 |
| `CaptureSheet` | confirm parsed transaction | pre-filled; low-confidence fields visibly outlined and focused first |
| `ReviewQueue` | unparsed captures | shows raw text verbatim so the user can see what VITT saw |
| `Pet` | gamification | layered SVG, ~25 pieces → ~576 looks via palette tokens |
| `SyncBadge` | sync state | quiet dot; `alarm` only when data is genuinely at risk |

---

## 6. Screens to mock up, in priority order

Each is a real Composable, built in a scratch screen and judged on device.

1. **Amount entry** — done. The most-used interaction; everything else is
   secondary.
2. **Add transaction** — keypad plus category, account, date, split toggle. The
   test: coffee logged in under 5 seconds and 3 taps.
3. **Ledger** — grouped by currency, then by day. The test: a bad month is
   readable without feeling like an indictment.
4. **Home** — per-currency balances, health ring, projection band. The hardest
   screen to keep honest, because it is where a blended total would be most
   tempting.
5. **Capture confirm** — the share-sheet landing. The test: high-confidence parse
   is one tap; low-confidence is obvious about what it is unsure of.
6. **Budgets** — envelopes per currency.
7. **Settings** — including the gamification tier switch (Off must be a complete
   product, not a punished mode) and "Disconnect Google", which Apple 5.1.1
   requires.

**Deliberately not mocked yet:** anything gamified beyond the pet's resting
state. Per PLAN.md §5, the off switch is built before the mechanics.

---

## 7. Motion

- **Nothing celebrates a transaction.** No confetti, no bounce, no sound. This is
  a named prohibition, not taste: celebration tied to activity is precisely what
  the Robinhood consent order enjoined.
- Transitions are 150–200ms, ease-out. Long enough to follow, short enough that
  logging an expense never feels gated on an animation.
- The pet idles; it does not react to individual transactions.
- **Respect reduced-motion.** Both platforms expose the setting, and financial
  data must never depend on an animation the user has turned off.

---

## 8. How this gets applied

Tokens live in Kotlin in `composeApp/src/commonMain/.../ui/theme/`, exposed
through a `MaterialTheme` colour scheme plus a small `VittTokens` object for what
Material does not model (tabular figures, the confidence stroke, currency
grouping). No JSON token pipeline: there is no second consumer to feed, and one
would add a build step that can drift from the code it describes.

Dark mode is derived from the same table above, not hand-tuned separately — one
palette, two resolutions.
