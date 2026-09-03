# VITT — how to design with this system

VITT is a multi-currency expense tracker with no backend: the user's own Google
Sheet is the store. **The refusal to convert currencies is the product**, so the
identity has to say *parallel*, never *combined*. Most of the rules below follow
from that one fact.

There are no components in this project, only tokens. VITT's UI is Kotlin
Compose drawing to a Skia canvas, so there is no React build to ship. Compose
these designs from plain elements and VITT's custom properties.

## Load order and setup

Import one file. Everything else arrives through its `@import` closure:

```html
<link rel="stylesheet" href="_ds/<folder>/styles.css">
```

Theme and accent are two independent attributes on a container. Set both; the
defaults are `cream` and `violet`:

```html
<div data-vitt-theme="cream" data-vitt-accent="violet"
     style="background: var(--vitt-ground); color: var(--vitt-ink);">
```

Without a `--vitt-ground` background the design inherits whatever is behind it
and the palette does nothing. Themes are `cream`, `paper`, `slate`, `dusk`.
Accents are `violet`, `clay`, `plum`. Dark themes resolve the accent to its
lifted variant automatically, so never hardcode the lifted hex.

## The styling idiom

**Custom properties, no class vocabulary.** There is exactly one utility class,
`.vitt-num`, and it goes on every element that shows an amount. Everything else
is `var(--vitt-*)` on your own elements.

| Family | Names |
|---|---|
| Ground and surface | `--vitt-ground`, `--vitt-surface`, `--vitt-card`, `--vitt-shadow`, `--vitt-hairline` |
| Text | `--vitt-ink`, `--vitt-ink-muted`, `--vitt-ink-faint` |
| Accent | `--vitt-accent` (live, mine, now), `--vitt-accent-soft` (income, settled) |
| Currency | `--vitt-currency-1` … `-6`, and `-1-dark` … `-6-dark` |
| Type | `--vitt-text-money-hero`, `-money`, `-display`, `-title`, `-body`, `-label`, `-caption`, each with `-weight` and some with `-tracking` / `-leading` |
| Space | `--vitt-space-hair`, `-tight`, `-snug`, `-base`, `-loose`, `-section` |
| Radius | `--vitt-radius-card`, `-key`, `-tile`, `-pill` |

## Rules that are not preferences

- **Never show a grand total, a net worth, or any figure summing two
  currencies.** Currencies are stacked as sibling ledgers, each complete on its
  own. The absence is stated in the UI so it reads as rigour rather than a gap.
- **A currency is a colour**, assigned in order from the fixed six. Never
  recolour them, never let an accent match one.
- **Money is never coloured by sentiment.** Spending is plain `--vitt-ink`;
  only income takes `--vitt-accent-soft`. Nothing floods red. Budget health
  runs accent → neutral, so over budget is the *absence* of accent and the card
  goes quiet rather than shouting. `--vitt-destructive` is for a destructive
  confirmation only.
- **Chroma lives in lines, dots and glows** — never a filled bar or a coloured
  card. This is what keeps the palette working at five currencies.
- **One loud number per screen.** `--vitt-text-money-hero` once, and never for
  bad news with a minus sign in front of it: state spending as a positive
  amount out.
- **A figure never appears without its own currency symbol**, because there is
  no default currency.
- **Voice: receipts, not coaching.** Past tense, no exclamation marks, no
  adjectives about the user's choices, never "should". Copy is short: one
  sentence where a sentence is needed, none where it is not. No em dashes.

## Where the truth lives

`_ds/<folder>/styles.css` and the four files it imports are the whole system;
`tokens/themes.css` carries the per-theme values and `tokens/palette.css` the
fixed currency hues with the reasoning attached.

## One idiomatic build

```html
<div data-vitt-theme="cream" data-vitt-accent="violet"
     style="background: var(--vitt-ground); padding: var(--vitt-space-loose);
            font-family: var(--vitt-font-body); color: var(--vitt-ink);">
  <div style="background: var(--vitt-card); border-radius: var(--vitt-radius-card);
              padding: var(--vitt-space-loose); box-shadow: 0 2px 10px var(--vitt-shadow);">
    <div style="display: flex; align-items: center; gap: var(--vitt-space-snug);">
      <span style="width: 8px; height: 8px; border-radius: 50%;
                   background: var(--vitt-currency-1);"></span>
      <span style="font-size: var(--vitt-text-caption);
                   font-weight: var(--vitt-text-caption-weight);
                   letter-spacing: var(--vitt-text-caption-tracking);
                   text-transform: uppercase; color: var(--vitt-ink-muted);">EUR</span>
    </div>
    <div class="vitt-num" style="font-size: var(--vitt-text-money-hero);
                                 font-weight: var(--vitt-text-money-hero-weight);
                                 letter-spacing: var(--vitt-text-money-hero-tracking);
                                 margin-top: var(--vitt-space-hair);">€1,240.00</div>
    <div style="font-size: var(--vitt-text-label); color: var(--vitt-ink-muted);">
      left of €1,800.00
    </div>
  </div>
</div>
```

The currency dot carries the hue; the card does not. That is the whole idiom.
