# Pip in motion — what works, what is enjoined

Asked: could the character move or perform actions, would it look good and feel
interactive, and would the layout have to change.

Short answer: **motion — yes, and some already ships. Reacting to what the user
just did — no, and not as a matter of taste.** The layout question has a clean
answer that also happens to be the cheap one.

## Two corrections to the premise

**Pip is not pixel art.** `ui/Pip.kt` draws a piggy bank with Compose `Canvas`
primitives — rounded ovals for the body and cheeks, arcs for the tail, circles for
the nostrils. Smooth vector shapes at any resolution. Moving to pixel art is a
change of visual identity, not an animation change, and it has a specific cost
(below).

**Pip already animates.** Two motions run today: a blink on a 5,500 ms cycle and
a bob on a 1,600 ms reverse tween. So "does animation work in Compose
Multiplatform on iOS" is already answered by the running app — and
`CADisableMinimumFrameDurationOnPhone` is already `true` in `Info.plist`, which is
exactly the key that lets the render loop exceed 60 Hz on ProMotion hardware. The
infrastructure question is closed.

## The line that must not be crossed

`Pip.kt:52` carries a comment from whoever wrote it:

> A slow blink is the only motion. It reads as alive without reacting to anything
> the user did, **which is the line this character must not cross.**

That is not fussiness. `PLAN.md` §5.5 lists, under **Do not build**:

> **Confetti / celebration on transactions** — **Enjoined by name** in the
> Robinhood Massachusetts consent order ($7.5M, Jan 2024).

A character that performs a happy action when you log an expense **is** that
mechanic with a different skin. The same section rules out points or badges for
logging (engagement-contingent rewards measure **d = −0.40**, the worst cell in
Deci et al. 1999), anything rewarding entry count (the Fortune City failure, where
reviewers reported *trying to spend more money* to unlock buildings), and
HP/damage/decay (Habitica: punishment fires exactly when the user can least
absorb it).

So the appealing version of this idea — tap Save, Pip does a little dance — is the
one specific thing the research phase cut. It is also the one that would feel best
in a demo, which is precisely why it is worth naming.

Two further constraints from §5.2 bound the sad direction:

- **Non-logging can never decrease health.** Pip must never look neglected,
  hungry, or asleep because the user stopped opening the app. Absence of an
  expense is not evidence of an expense.
- **`coverage < 0.40` suppresses the score entirely** — "not enough to say yet",
  never a low score. Pip's mood inherits that: no data means neutral, not sad.

The organising principle behind all of it (§5.1) is the ostrich effect: people
avoid checking their finances exactly when things are going badly. Every mechanic
is judged on *does this make opening the app on a bad day cheaper or more
expensive?* A character visibly disappointed in you is the most expensive thing
that could be on the home screen.

## What motion is sanctioned

§5.4 does ship a pet: **"Layered-SVG pet, mood from count of healthy
envelopes."** That is the permitted reactive channel, and it is worth reading
precisely:

| Reacts to | Allowed | Why |
|---|---|---|
| Number of envelopes on track | **Yes** — this is the shipped design | An outcome, not an action. Changes slowly, so it cannot be farmed. |
| Days recorded in the window | Already does — colour deepens | §5.4 rewards *per-day-observed, not per-entry*, which is the anti-Fortune-City rule. Coverage is a gate, not a prize. |
| A transaction being saved | **No** | The enjoined mechanic. |
| Amount spent | **No** | "Nothing in the economy may be monotonically increasing in money spent." |
| Absence of logging | **No** | Non-logging can never decrease anything. |

So the honest design space is **idle life plus slow mood**, not **reaction**:

- **Idle variety.** Instead of one bob, a small pool of idles chosen at random on
  a slow timer — a stretch, a head turn, an ear twitch, a settle. This is what
  actually reads as "alive" rather than "looping", and it is free of the problem,
  because nothing the user does triggers it.
- **Posture from mood.** Envelope health picks the idle *set* and the resting
  posture, so Pip is perkier in a good month without ever performing at the
  moment of a log. It changes on a timescale of days, which is the point.
- **Coins, maybe.** The one motion that is arguably about a transaction and still
  defensible: a coin resting in its currency's slot as a *state*, not a coin
  dropping in as a *celebration*. The distinction is real but thin, and it is the
  place to be most careful.

Note also that `docs/design-identity.md` puts the loud gamification on the
**Habit** tab — *"the one place gamification is loud"*. An expressive, roaming Pip
probably belongs there. The Ledgers screen is governed by "one loud number per
screen" and a deliberately quiet tone; a character moving around competes with the
figure the screen exists to show.

## The layout question

The instinct that it "would change the layout of the page slightly" is exactly the
thing to design out. Pip is an item in a `LazyColumn`:

```kotlin
Pip(modifier = Modifier.fillMaxWidth().height(140.dp))
```

If motion changed that box, every card below it would remeasure on every frame.
In a lazy list that is worse than it sounds: item offsets shift under the user's
thumb, and scroll position can jump. A character that "moves around the page" in
the sense of *displacing content* would make the home screen feel broken, not
alive.

**Rule: reserve the box up front and let Pip roam inside it.** Motion belongs at
draw time, never at layout time:

- `Modifier.graphicsLayer { translationX = …; translationY = … }` — cheapest;
  changes neither measurement nor the recorded draw list.
- `Modifier.offset { IntOffset(…) }` (the lambda overload) — also layout-safe.
- **Never** animated `padding`, `size`, `height`, or `Arrangement.spacedBy`, and
  never a `Spacer` whose height animates. Those all run a layout pass.

Today's `Canvas(modifier = modifier.offset(y = (-5).dp * bob))` uses the
non-lambda `offset`, which reads the value at composition rather than at draw —
correct in effect but it recomposes each frame. Switching to `graphicsLayer` would
make the existing bob cheaper before adding anything to it.

Practically: if Pip should have room to move, give the slot the space it needs at
its widest and tallest, and accept the whitespace. That is a design decision about
how much of the fold to spend on a character, and it is the real cost — not the
animation.

## If it should actually be pixel art

The tempting part of pixel art is that motion is cheap and characterful: 8–12 fps
of chunky frames reads better than smooth interpolation, and costs less.

The problem is specific. **Pip's colour is entirely palette-driven**, and one part
of that is load-bearing. The coin slots are coloured per currency:

```kotlin
(0 until currencyCount).map { colors.currency(it) }
```

`design-identity.md` says this is what *"makes the app's one hard rule visible
without a word of explanation"* — one slot per currency, coins never move between
them. A pre-rendered sprite sheet freezes those colours, so it would lose:

- the per-currency slot hues, and with them that piece of explanation
- light/dark palette response (`VittColors.light()` / `.dark()`)
- the streak warmth ramp, which is computed from the palette at draw time

None of that is fatal, but it must be answered before committing:

1. **Draw the slots separately** over a pixel body — keeps the colour, costs a
   compositing step and some pixel-alignment care.
2. **Palette-swap at runtime** — store a 1-bit or indexed mask and map indices to
   theme colours when drawing. Keeps everything themable; more work.
3. **Draw pixel art with rects** rather than a bitmap. At 32×32 this is a few
   hundred `drawRect` calls, fully palette-driven and with no asset pipeline. The
   most likely right answer at this scale.

If a bitmap is used anyway: `filterQuality = FilterQuality.None` on `drawImage`,
or bilinear filtering will blur every edge — the single most common pixel-art
rendering mistake. And the sprite must be scaled by a whole-number factor and
positioned on whole device pixels, or it will shimmer while moving. At 3× on
iPhone, a 32 px sprite drawn at 4× is 128 device pixels; fractional offsets during
a tween are what cause the crawling look.

## Prerequisites, before any of this ships

- **The off switch comes first.** `CLAUDE.md` and §5 both state it: Gentle tier
  only, off switch first. Any Pip motion needs to be disableable before it lands,
  not after.
- **Respect Reduce Motion.** Pip's bob currently does not check it. iOS exposes
  `UIAccessibility.isReduceMotionEnabled` and Android has
  `ANIMATOR_DURATION_SCALE`; this needs an `expect`/`actual`. A roaming character
  that ignores the setting is both an accessibility failure and something a
  reviewer can reasonably flag. **This is a gap in what already ships**, not just
  in what might be added.
- **Battery.** An infinite transition invalidates every frame for as long as the
  item is composed. `LazyColumn` disposes off-screen items, so scrolling away
  stops it — but while the home screen is open, motion runs continuously, and at
  120 Hz on ProMotion. Stepped idles at 8–12 fps still tick the animation clock;
  if this matters, drive idles from a coarse `delay()` loop rather than a
  continuous transition.

## Recommendation

**Do:** a pool of random slow idles, mood-selected by envelope health, inside a
fixed box, using `graphicsLayer`, behind the off switch, respecting Reduce Motion.
Keep it vector so the palette keeps working. Consider putting the expressive
version on the Habit tab and leaving the home screen's Pip as quiet as it is now.

**Don't:** anything that fires on save, anything that looks disappointed, anything
that changes the layout.

**Unverified here.** Nothing in this document has been prototyped. The claims
about Compose animation on iOS rest on the fact that Pip's blink and bob already
run on a real device (`docs/phase-1-results.md`); the pixel-art rendering
specifics — `FilterQuality.None`, integer scaling, sprite atlases in
`composeResources` — have not been tried in this project and should be spiked
before anyone plans around them.
