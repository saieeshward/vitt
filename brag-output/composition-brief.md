# Hyperframes Composition Brief: VITT

## Objective
Create a short launch-style brag video for VITT.

## Output
- Composition directory: `brag-output/composition/`
- Rendered video: `brag-output/brag.mp4`
- Format: landscape — 1920x1080
- Duration: 22 seconds

## Source Material
- Project root: the VITT repository
- Primary files read: `site/index.html`, `site/assets/css/site.css`, `site/assets/js/pets.js` (the app's companion sprites), `README.md`, `composeApp/.../ui/screens/AddScreen.kt` (the Add sheet and its usuals row), `shared/.../sheets/DerivedTransactions.kt` (the Transactions tab columns)
- Product name: VITT
- Tagline / strongest claim: "Money in more than one currency, kept apart."
- Key UI or visual moment to recreate: the Add sheet with the "Coffee Angel €3.50" usual, tapped and saved; the Transactions tab gaining a row; the site's three coin stacks
- Copy that must appear verbatim:
  - Money in more than one currency, kept apart.
  - A spreadsheet you own. No account, no server.
  - Two taps.

## Creative Direction
- Tone preset: deadpan
- Creative direction: a bank statement that refuses to do the sum
- Interpretation: one thought at a time on a quiet cream ground, large type, long holds, slow crossfades, dry physical sounds
- Angle: every money app adds euro, rupees and dollars into one number; VITT will not, and the video shows the refusal as three stacks that never touch
- Hook: three balances land one by one, then "Most apps add these up."
- Outro / punchline: Penny beside "VITT", then the tagline, then the address
- Avoid:
  - Generic SaaS language
  - Abstract filler visuals
  - Unrelated visual redesign
  - Any coin passing over another currency's stack

## Visual Identity
- Background: #FFFDF7
- Text: #241F33; muted #5F5970
- Accent: #6B5BFF (Save, selected chip, the new-row wash)
- Currency hues: EUR #2D9D75, INR #BB7F0F, USD #2C8CF6, in rings, dots and coin faces only
- Display font: Geist 600, shipped locally (`assets/fonts/`)
- Body font: Geist 400/500
- Visual references from the project: the site hero's glass stacks with coloured rim rings, the app's chips and keypad, the companion sprite

## Storyboard
Use the storyboard in `brag-output/brag-plan.md` as the creative contract.

Scene summary:
1. Three balances — 4.4s — €1,877.74 / ₹12,000.00 / $240.00, then "Most apps add these up."
2. Kept apart — 4.4s — three stacks, coins drop each into its own; "VITT keeps them apart."
3. Two taps — 6.4s — Add sheet: tap the usual, tap Save, the Activity row appears; "Two taps."
4. Your sheet — 3.8s — a row appends to the Transactions tab; "A spreadsheet you own. No account, no server."
5. VITT — 3.0s — Penny, the name, the tagline, the address

## Audio
- Audio role: warm bed with sparse physical accents
- Audio arc: low steady bed from the start, accents on the three landings, the coin drops, the two taps and the row; fade out over the last 1.2s
- Music: `assets/music/happy-beats-business-moves-vol-12-by-ende-dot-app.mp3` at 0.16
- Music treatment: volume lane, 0.16 held, down to 0 from 20.8s to 22.0s
- Music cue guidance: bundled preset `brag/skills/brag/assets/music/cues/happy-beats-business-moves-vol-12-by-ende-dot-app.music-cues.json` (109.96 BPM). Beat-lock: Scene 3's phone lands at 8.74s, the sheet row lands at 17.47s, "VITT" lands at 19.66s. Beat grid for the six coin drops: 5.34, 6.00, 6.56, 7.09, 7.64, 8.19.
- Audio-reactive treatment: none, by choice (deadpan stillness)
- Audio-coupled moments:
  - Scene 1 — three soft chip lays, one per figure
  - Scene 2 — chips-stack on the first and last coin only
  - Scene 3 — one click on the usual, one on Save
  - Scene 4 — one soft drop as the row lands
  - Scene 5 — one low bong as the name lands
- SFX selection guidance: low high-frequency-risk files (`casino/chip-lay-*`, `casino/chips-stack-*`, `interface/click_003`, `interface/drop_001`, `interface/bong_001`) at 0.45-0.65
- Exact SFX choice: chosen against the implemented animation below
- Audio files: copied into `brag-output/composition/assets/`

## Hyperframes Instructions
Built against `hyperframes-core`, `hyperframes-animation`, `hyperframes-keyframes` and `hyperframes-cli` (read from the Hyperframes repository), without the entry-point interview. One standalone `index.html`, one paused GSAP timeline, clips crossfading on alternating tracks.

Requirements:
- Show at least one real UI, copy, or visual element from the source project.
- Keep all text readable in the final render.
- Keep the video within 15-25 seconds.
- Include the planned music and SFX layer.
- Run `hyperframes check` before render.
