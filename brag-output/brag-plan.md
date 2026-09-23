# Brag Plan: VITT

## What is this app?
An expense tracker for iPhone and Android for people whose money lives in more than one currency, which keeps every currency's balance apart and never converts one into another.

## The angle
Every money app does one thing VITT refuses to: add euro, rupees and dollars into one number with an exchange rate. The video says so in a calm voice and then simply shows the refusal. Three balances that other apps would sum instead drop, coin by coin, into three stacks that never touch. The joke is the restraint: the most exciting thing this app does is not do maths it cannot do honestly.

## Hook (first 2-3 seconds)
Three balances land one at a time on the cream ground, each with its currency dot: €1,877.74, ₹12,000.00, $240.00. A quiet line settles under them: "Most apps add these up." Nothing else moves. The viewer waits for the total that never comes.

## Key moments (the middle)
- The three figures fall apart into three glass stacks, one per currency hue, and coins drop into their own stack and no other: "VITT keeps them apart."
- The app in use: the Add sheet, a tap on the "Coffee Angel €3.50" usual, the amount fills in, a tap on Save. "Two taps."
- The entry lands as a new row in the user's own Google Sheet: "A spreadsheet you own. No account, no server."

## Outro / punchline
Today's companion, the pixel piggy bank Penny, with a coin slot for each currency on her back, stands beside the name. "VITT." Then the site's own line, "Money in more than one currency, kept apart." Long hold.

## User flow worth showing
Open Add → tap the usual "Coffee Angel €3.50" → Save → the row appears in Activity and in the sheet. This is the real flow in `composeApp/.../ui/screens/AddScreen.kt` (the usuals row) and the append in `shared/.../sheets/DerivedTransactions.kt`.

## Tone
- Preset: deadpan
- Creative direction: a bank statement that refuses to do the sum
- Interpretation: large type on a quiet cream ground, one thought at a time, long holds, slow crossfades, no exclamation marks, sound kept to a low bed and a few dry, physical cues (coins, one click, one landing).

## Format: landscape — 1920x1080
## Duration: 22 seconds

## Visual identity (from the project)
- Background: #FFFDF7 (cream ground, `site/assets/css/site.css` `--ground`)
- Surface: #F2EFE7; card #FFFFFF
- Text: #241F33 (`--ink`), muted #5F5970
- Accent: #6B5BFF (violet, only for what is live: the Save button, the selected chip)
- Currency hues: EUR #2D9D75, INR #BB7F0F, USD #2C8CF6 (in lines, rings, dots and coin faces, never filled cards)
- Display font: Geist, semibold, tight tracking
- Body font: Geist
- Strongest visual element: the site's three coin stacks, and the companion sprite drawn from the app's own pixel data (`site/assets/js/pets.js`)

## Share copy (draft)
I made VITT. It tracks money in more than one currency and never adds them together.

## Audio direction
- Role: warm bed with sparse, physical accents
- Music: happy-beats-business-moves-vol-12-by-ende-dot-app.mp3 (steady, clean; the deadpan recommendation) at 0.16
- Music treatment: in from 0 at low level, slight fade under the outro, fade to silence over the last 1.2s
- Music cue guidance: bundled preset read (109.96 BPM). Strong cues in window: 8.74s, 13.11s, 17.47s, 19.66s. Use 8.74s for the Add-sheet scene arrival, 17.47s for the sheet row landing, 19.66s for the outro name (the first strong cue inside Scene 5). Beat grid for the coin drops in Scene 2: 5.34, 6.00, 6.56, 7.09, 7.64, 8.19.
- Audio-reactive treatment: none. Deadpan stillness is the point; nothing on screen breathes with the music.
- SFX posture: sparse, motion-matched
- Audio-coupled moments: the three figures landing (soft chip lay), coins stacking (chips-stack, accenting the first and last drops only), the usual chip tap and Save (one clean click each), the sheet row landing (one soft drop), the outro name (one warm, low bong or nothing)
- Restraint rule: no whooshes, no risers, nothing celebratory; no sound on every coin

## Storyboard

### Scene 1 — Three balances — 4.4s
Cream ground. Three figures arrive one at a time left to right, each a large tabular numeral with its currency dot above: €1,877.74 (green), ₹12,000.00 (amber), $240.00 (blue). Then the line "Most apps add these up." settles below and holds (about 1.8s settled).
Sequential/interaction: yes — the three figures land one by one, about 0.45s apart.
Audio intent: quiet, precise; the bed is barely there.
Audio-coupled idea: a soft chip-lay on each figure's landing, very low.
Music: low bed starting.
Transition mood: slow crossfade → Scene 2

### Scene 2 — Kept apart — 4.4s
The figures shrink and slide down into three glass stacks, one per hue, the site's hero. Coins drop in from above, each into its own stack only, two rounds. The line "VITT keeps them apart." holds for the second half.
Sequential/interaction: yes — coins drop one by one on the beat grid (5.34 to 8.19), alternating stacks; each always lands in its own colour.
Audio intent: tactile, satisfying, still calm.
Audio-coupled idea: chips-stack on the first and last drops only.
Transition mood: slow crossfade → Scene 3

### Scene 3 — Two taps — 6.4s
A phone on the right showing VITT's Add sheet recreated from the app: Cancel, New, Save; Expense/Income chips; the usual chip "Coffee Angel €3.50"; the amount display at €0.00; the keypad. A soft cursor dot taps the usual chip: it highlights and the display fills to €3.50. It taps Save. The sheet slides down to Activity with the new top row "Coffee Angel · Dining · €3.50". On the left, "Two taps." holds, then "The account and category fill themselves in." in muted text.
Sequential/interaction: yes — simulated tap on the usual, then on Save.
Audio intent: small, clean interaction sounds.
Audio-coupled idea: one click per tap.
Transition mood: slow crossfade → Scene 4

### Scene 4 — Your sheet — 3.8s
A spreadsheet card, "VITT · Transactions", with a header row and four earlier rows. A new row appends at the bottom with a brief violet wash: 2026-09-23 · Coffee Angel · Dining · EUR · −3.50. The line "A spreadsheet you own. No account, no server." holds.
Sequential/interaction: yes — the new row slides in and lands near 17.47s.
Audio intent: one soft landing.
Audio-coupled idea: a soft drop as the row lands.
Transition mood: slow crossfade → Scene 5

### Scene 5 — VITT — 3.0s
The companion Penny (pixel art from the app's sprite data) stands on the left of the name. "VITT" in large type, then "Money in more than one currency, kept apart." and small "saieeshward.github.io/vitt". Long hold; music fades out.
Sequential/interaction: none.
Audio intent: settle and stop.
Audio-coupled idea: none, or one low warm accent as the name lands.
Transition mood: hold to end.

Scene durations: 4.4 + 4.4 + 6.4 + 3.8 + 3.0 = 22.0s.

**Music mood for this video:** deadpan
**Audio summary:** a steady low bed under a handful of dry, physical sounds — coins, two taps, one landing — that fades out on the name.

Note on data: every figure, merchant and date is from the app's own sample data or invented for the video; no real person's data appears.
