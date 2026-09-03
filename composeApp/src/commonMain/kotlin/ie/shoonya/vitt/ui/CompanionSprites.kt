package ie.shoonya.vitt.ui

/**
 * The companion sprites, as authored in the design file.
 *
 * Ported rather than redrawn. Artboard 14a of the design project renders all six
 * animals as inline SVG `<rect>` elements on a `0 0 24 20` viewBox, grouped by
 * rig part, and this is that art transcribed: same coordinates, same colours,
 * same grouping. An earlier pass here invented five animals of its own, which
 * was work thrown away — the design had six, judged against a rule, with two
 * deliberately rejected.
 *
 * **Encoded as strings, decoded once.** Sixteen hundred rectangles written out
 * as Kotlin constructor calls is a large file to compile and a worse one to
 * read; as text it is compact and diffable against the design, and decoding it
 * on first access costs microseconds at startup. The format is one rect per
 * `/`, fields space-separated: `x y w h colour part`.
 *
 * `colour` is either a six-digit hex or `c0`..`c5`, which means *the palette's
 * currency hue at that index* rather than a fixed colour. The design already
 * authors those markers in the app's own currency colours (`#35B98A`,
 * `#E39A12`, `#4C9DF7` are exactly `Palette.CurrencyLight[0..2]`), so binding
 * them to the palette keeps "a currency is a colour" true in both themes rather
 * than freezing it to the light one.
 *
 * `part` is the rig part, matching the design's own group names:
 * ```
 * b body   h head   e ear   y eye   m mouth   t tail   l leg (ungrouped)
 * ```
 */
internal object CompanionSprites {

    /** The sprite grid, from the design's viewBox. */
    const val WIDTH = 24
    const val HEIGHT = 20

    // ── Penny: pig, coin slots on her back ──
    private const val PENNY_STAND =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 8 3 3 2A1520 y/8 8 3 3 2A1520 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/3 14 3 3 E0709A l/7 14 3 3 E0709A l"
    private const val PENNY_WALK =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 8 3 3 2A1520 y/8 8 3 3 2A1520 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/3 14 3 3 E0709A l/7 14 3 3 E0709A l"
    private const val PENNY_FRONT =
        "6 16 4 3 B84E74 l/14 16 4 3 B84E74 l/4 4 16 12 E0709A l/5 3 14 1 E0709A l/3 6 1 8 E0709A l/20 6 1 8 E0709A l/4 14 16 2 B84E74 l/5 1 4 2 B84E74 e/15 1 4 2 B84E74 e/6 0 2 1 B84E74 e/16 0 2 1 B84E74 e/8 1 2 2 c0 l/14 1 2 2 c1 l/6 7 4 5 2A1520 y/14 7 4 5 2A1520 y/6 7 2 2 ffffff y/14 7 2 2 ffffff y/3 11 3 2 F5A6C2 l/18 11 3 2 F5A6C2 l/9 11 6 4 C95C87 l/10 12 2 2 7E3252 l/12 12 2 2 7E3252 l"
    private const val PENNY_CONTENT =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 8 3 3 2A1520 y/8 8 3 3 2A1520 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/3 14 3 3 E0709A l/7 14 3 3 E0709A l"
    private const val PENNY_EXPECT =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 6 3 5 2A1520 y/8 6 3 5 2A1520 y/2 6 2 2 ffffff y/8 6 2 2 ffffff y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/3 14 3 3 E0709A l/7 14 3 3 E0709A l/15 0 1 1 FFE9A8 l/15 1 1 2 FFE9A8 l"
    private const val PENNY_PECKISH =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 7 3 4 2A1520 y/8 7 3 4 2A1520 y/3 7 2 2 ffffff y/9 7 2 2 ffffff y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/5 12 3 2 7E3252 m/3 14 3 3 E0709A l/7 14 3 3 E0709A l/15 1 3 1 5FD3A8 l/14 2 5 2 c0 l/15 4 3 1 1F7F5F l"
    private const val PENNY_CAREFUL =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 8 3 3 2A1520 y/8 8 3 3 2A1520 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/1 6 4 1 B84E74 y/8 7 4 1 B84E74 y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/4 13 5 1 7E3252 h/3 14 3 3 E0709A l/7 14 3 3 E0709A l/16 1 2 2 c1 l/16 1 2 1 FFC94D l"
    private const val PENNY_GLAD =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 9 1 1 2A1520 y/3 8 1 1 2A1520 y/4 9 1 1 2A1520 y/8 9 1 1 2A1520 y/9 8 1 1 2A1520 y/10 9 1 1 2A1520 y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/3 14 3 3 E0709A l/7 14 3 3 E0709A l/14 1 2 2 FFE9A8 l/19 3 2 2 5EE0AC l"
    private const val PENNY_SLEEPY =
        "13 14 3 3 B84E74 l/20 7 2 1 E0709A t/22 5 2 2 E0709A t/10 7 10 7 E0709A b/11 6 8 1 E0709A b/10 12 10 2 B84E74 b/12 4 2 2 c0 b/16 4 2 2 c1 b/16 14 3 3 E0709A l/1 4 11 10 E0709A h/2 3 9 1 E0709A h/0 6 1 6 E0709A h/12 6 1 6 E0709A h/2 1 3 2 B84E74 e/8 1 3 2 B84E74 e/3 0 1 1 B84E74 e/9 0 1 1 B84E74 e/2 9 3 1 2A1520 y/8 9 3 1 2A1520 y/2 8 3 1 B84E74 y/8 8 3 1 B84E74 y/0 11 2 2 F5A6C2 h/11 11 2 2 F5A6C2 h/4 11 5 3 C95C87 h/5 12 1 2 7E3252 h/7 12 1 2 7E3252 h/3 14 3 3 E0709A l/7 14 3 3 E0709A l/14 2 4 1 7FE3C0 l/17 3 1 1 7FE3C0 l/16 4 1 1 7FE3C0 l/15 5 1 1 7FE3C0 l/14 6 4 1 7FE3C0 l"

    // ── Acorn: squirrel, acorns on her back ──
    private const val ACORN_STAND =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/3 14 3 3 C98147 l/7 14 3 3 C98147 l"
    private const val ACORN_WALK =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/3 14 3 3 C98147 l/7 14 3 3 C98147 l"
    private const val ACORN_FRONT =
        "6 16 4 3 AA6B36 l/14 16 4 3 AA6B36 l/4 4 16 12 C98147 l/5 3 14 1 C98147 l/3 6 1 8 C98147 l/20 6 1 8 C98147 l/4 14 16 2 AA6B36 l/5 0 4 3 C98147 e/15 0 4 3 C98147 e/5 0 4 1 E9B98C e/15 0 4 1 E9B98C e/8 1 3 1 8A5A2B l/8 2 3 2 c0 l/13 1 3 1 8A5A2B l/13 2 3 2 c1 l/6 7 4 5 2E1C12 y/14 7 4 5 2E1C12 y/6 7 2 2 ffffff y/14 7 2 2 ffffff y/3 11 3 2 E0A97A l/18 11 3 2 E0A97A l/10 11 4 4 E9B98C l/11 13 2 1 6B4326 l"
    private const val ACORN_CONTENT =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/3 14 3 3 C98147 l/7 14 3 3 C98147 l"
    private const val ACORN_EXPECT =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 6 3 5 2E1C12 y/8 6 3 5 2E1C12 y/2 6 2 2 ffffff y/8 6 2 2 ffffff y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/3 14 3 3 C98147 l/7 14 3 3 C98147 l/15 0 1 1 FFE9A8 l/15 1 1 2 FFE9A8 l"
    private const val ACORN_PECKISH =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 7 3 4 2E1C12 y/8 7 3 4 2E1C12 y/3 7 2 2 ffffff y/9 7 2 2 ffffff y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/5 12 3 2 6B4326 m/3 14 3 3 C98147 l/7 14 3 3 C98147 l/15 1 3 1 5FD3A8 l/14 2 5 2 c0 l/15 4 3 1 1F7F5F l"
    private const val ACORN_CAREFUL =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/1 6 4 1 AA6B36 y/8 7 4 1 AA6B36 y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/4 13 5 1 6B4326 h/3 14 3 3 C98147 l/7 14 3 3 C98147 l/16 1 2 2 c1 l/16 1 2 1 FFC94D l"
    private const val ACORN_GLAD =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 9 1 1 2E1C12 y/3 8 1 1 2E1C12 y/4 9 1 1 2E1C12 y/8 9 1 1 2E1C12 y/9 8 1 1 2E1C12 y/10 9 1 1 2E1C12 y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/3 14 3 3 C98147 l/7 14 3 3 C98147 l/14 1 2 2 FFE9A8 l/19 3 2 2 5EE0AC l"
    private const val ACORN_SLEEPY =
        "13 14 3 3 AA6B36 l/18 6 4 4 D99A63 t/20 3 4 4 D99A63 t/18 1 5 3 D99A63 t/15 0 4 2 D99A63 t/20 6 4 1 C08145 t/18 3 2 1 C08145 t/10 7 10 7 C98147 b/11 6 8 1 C98147 b/10 12 10 2 AA6B36 b/11 4 3 1 8A5A2B b/11 5 3 2 c0 b/15 4 3 1 8A5A2B b/15 5 3 2 c1 b/16 14 3 3 C98147 l/1 4 11 10 C98147 h/2 3 9 1 C98147 h/0 6 1 6 C98147 h/12 6 1 6 C98147 h/2 0 3 3 C98147 e/8 0 3 3 C98147 e/2 0 3 1 E9B98C e/8 0 3 1 E9B98C e/2 9 3 1 2E1C12 y/8 9 3 1 2E1C12 y/2 8 3 1 AA6B36 y/8 8 3 1 AA6B36 y/0 11 2 2 E0A97A h/11 11 2 2 E0A97A h/5 11 4 3 E9B98C h/6 13 2 1 6B4326 h/3 14 3 3 C98147 l/7 14 3 3 C98147 l/14 2 4 1 7FE3C0 l/17 3 1 1 7FE3C0 l/16 4 1 1 7FE3C0 l/15 5 1 1 7FE3C0 l/14 6 4 1 7FE3C0 l"

    // ── Kumo: capybara, birds riding along ──
    private const val KUMO_STAND =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l"
    private const val KUMO_WALK =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l"
    private const val KUMO_FRONT =
        "6 16 4 3 8B6440 l/14 16 4 3 8B6440 l/4 4 16 12 A87C55 l/5 3 14 1 A87C55 l/3 6 1 8 A87C55 l/20 6 1 8 A87C55 l/4 14 16 2 8B6440 l/6 2 2 2 8B6440 e/16 2 2 2 8B6440 e/7 1 3 2 c0 l/11 0 3 2 c1 l/15 1 3 2 c2 l/6 7 4 5 2E1C12 y/14 7 4 5 2E1C12 y/6 7 2 2 ffffff y/14 7 2 2 ffffff y/3 11 3 2 C49871 l/18 11 3 2 C49871 l/7 11 10 4 B98C63 l/9 12 1 1 5A3D26 l/14 12 1 1 5A3D26 l"
    private const val KUMO_CONTENT =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l"
    private const val KUMO_EXPECT =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 6 3 5 2E1C12 y/8 6 3 5 2E1C12 y/2 6 2 2 ffffff y/8 6 2 2 ffffff y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l/15 0 1 1 FFE9A8 l/15 1 1 2 FFE9A8 l"
    private const val KUMO_PECKISH =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 7 3 4 2E1C12 y/8 7 3 4 2E1C12 y/3 7 2 2 ffffff y/9 7 2 2 ffffff y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/5 12 3 2 5A3D26 m/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l/15 1 3 1 5FD3A8 l/14 2 5 2 c0 l/15 4 3 1 1F7F5F l"
    private const val KUMO_CAREFUL =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/1 6 4 1 8B6440 y/8 7 4 1 8B6440 y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/4 13 5 1 5A3D26 h/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l/16 1 2 2 c1 l/16 1 2 1 FFC94D l"
    private const val KUMO_GLAD =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 9 1 1 2E1C12 y/3 8 1 1 2E1C12 y/4 9 1 1 2E1C12 y/8 9 1 1 2E1C12 y/9 8 1 1 2E1C12 y/10 9 1 1 2E1C12 y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l/14 1 2 2 FFE9A8 l/19 3 2 2 5EE0AC l"
    private const val KUMO_SLEEPY =
        "13 14 3 3 8B6440 l/20 9 2 1 8B6440 t/10 7 10 7 A87C55 b/11 6 8 1 A87C55 b/10 12 10 2 8B6440 b/11 4 3 2 c0 b/13 5 1 1 c0 b/15 3 3 2 c1 b/17 4 1 1 c1 b/18 4 2 2 c2 b/16 14 3 3 A87C55 l/1 4 11 10 A87C55 h/2 3 9 1 A87C55 h/0 6 1 6 A87C55 h/12 6 1 6 A87C55 h/2 2 2 2 8B6440 e/9 2 2 2 8B6440 e/2 9 3 1 2E1C12 y/8 9 3 1 2E1C12 y/2 8 3 1 8B6440 y/8 8 3 1 8B6440 y/0 11 2 2 C49871 h/11 11 2 2 C49871 h/3 11 7 3 B98C63 h/4 12 1 1 5A3D26 h/8 12 1 1 5A3D26 h/3 14 3 3 A87C55 l/7 14 3 3 A87C55 l/14 2 4 1 7FE3C0 l/17 3 1 1 7FE3C0 l/16 4 1 1 7FE3C0 l/15 5 1 1 7FE3C0 l/14 6 4 1 7FE3C0 l"

    // ── Biscuit: hamster, full cheeks ──
    private const val BISCUIT_STAND =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l"
    private const val BISCUIT_WALK =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l"
    private const val BISCUIT_FRONT =
        "6 16 4 3 C99655 l/14 16 4 3 C99655 l/4 4 16 12 E8B87A l/5 3 14 1 E8B87A l/3 6 1 8 E8B87A l/20 6 1 8 E8B87A l/4 14 16 2 C99655 l/5 1 4 3 C99655 e/15 1 4 3 C99655 e/6 2 2 1 F5D5AC e/16 2 2 1 F5D5AC e/1 9 3 5 c0 l/20 9 3 5 c1 l/6 7 4 5 2E1C12 y/14 7 4 5 2E1C12 y/6 7 2 2 ffffff y/14 7 2 2 ffffff y/10 12 4 3 F5D5AC l/11 12 2 1 6B4326 l"
    private const val BISCUIT_CONTENT =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l"
    private const val BISCUIT_EXPECT =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 6 3 5 2E1C12 y/8 6 3 5 2E1C12 y/2 6 2 2 ffffff y/8 6 2 2 ffffff y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l/15 0 1 1 FFE9A8 l/15 1 1 2 FFE9A8 l"
    private const val BISCUIT_PECKISH =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 7 3 4 2E1C12 y/8 7 3 4 2E1C12 y/3 7 2 2 ffffff y/9 7 2 2 ffffff y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/5 12 3 2 6B4326 m/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l/15 1 3 1 5FD3A8 l/14 2 5 2 c0 l/15 4 3 1 1F7F5F l"
    private const val BISCUIT_CAREFUL =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/1 6 4 1 C99655 y/8 7 4 1 C99655 y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/4 13 5 1 6B4326 h/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l/16 1 2 2 c1 l/16 1 2 1 FFC94D l"
    private const val BISCUIT_GLAD =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 9 1 1 2E1C12 y/3 8 1 1 2E1C12 y/4 9 1 1 2E1C12 y/8 9 1 1 2E1C12 y/9 8 1 1 2E1C12 y/10 9 1 1 2E1C12 y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l/14 1 2 2 FFE9A8 l/19 3 2 2 5EE0AC l"
    private const val BISCUIT_SLEEPY =
        "13 14 3 3 C99655 l/20 9 2 1 C99655 t/10 7 10 7 E8B87A b/11 6 8 1 E8B87A b/10 12 10 2 C99655 b/16 14 3 3 E8B87A l/1 4 11 10 E8B87A h/2 3 9 1 E8B87A h/0 6 1 6 E8B87A h/12 6 1 6 E8B87A h/2 1 3 3 C99655 e/8 1 3 3 C99655 e/3 2 1 1 F5D5AC e/9 2 1 1 F5D5AC e/2 9 3 1 2E1C12 y/8 9 3 1 2E1C12 y/2 8 3 1 C99655 y/8 8 3 1 C99655 y/0 9 2 4 c0 h/11 9 2 4 c1 h/5 12 3 2 F5D5AC h/6 12 1 1 6B4326 h/3 14 3 3 E8B87A l/7 14 3 3 E8B87A l/14 2 4 1 7FE3C0 l/17 3 1 1 7FE3C0 l/16 4 1 1 7FE3C0 l/15 5 1 1 7FE3C0 l/14 6 4 1 7FE3C0 l"

    // ── Mochi: cat, tags on her collar ──
    private const val MOCHI_STAND =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 8 3 3 2A2532 y/8 8 3 3 2A2532 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l"
    private const val MOCHI_WALK =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 8 3 3 2A2532 y/8 8 3 3 2A2532 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l"
    private const val MOCHI_FRONT =
        "6 16 4 3 6E7189 l/14 16 4 3 6E7189 l/4 4 16 12 8C8FA8 l/5 3 14 1 8C8FA8 l/3 6 1 8 8C8FA8 l/20 6 1 8 8C8FA8 l/4 14 16 2 6E7189 l/4 0 1 1 8C8FA8 e/4 1 2 1 8C8FA8 e/4 2 3 1 8C8FA8 e/19 0 1 1 8C8FA8 e/18 1 2 1 8C8FA8 e/17 2 3 1 8C8FA8 e/4 14 16 1 4A4A5C l/7 15 2 1 c0 l/11 15 2 1 c1 l/15 15 2 1 c2 l/6 7 4 5 2A2532 y/14 7 4 5 2A2532 y/6 7 2 2 ffffff y/14 7 2 2 ffffff y/3 11 3 2 A9AABF l/18 11 3 2 A9AABF l/11 11 2 1 E8A6B4 l/10 12 4 1 6B4655 l"
    private const val MOCHI_CONTENT =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 8 3 3 2A2532 y/8 8 3 3 2A2532 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l"
    private const val MOCHI_EXPECT =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 6 3 5 2A2532 y/8 6 3 5 2A2532 y/2 6 2 2 ffffff y/8 6 2 2 ffffff y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l/15 0 1 1 FFE9A8 l/15 1 1 2 FFE9A8 l"
    private const val MOCHI_PECKISH =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 7 3 4 2A2532 y/8 7 3 4 2A2532 y/3 7 2 2 ffffff y/9 7 2 2 ffffff y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/5 12 3 2 6B4655 m/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l/15 1 3 1 5FD3A8 l/14 2 5 2 c0 l/15 4 3 1 1F7F5F l"
    private const val MOCHI_CAREFUL =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 8 3 3 2A2532 y/8 8 3 3 2A2532 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/1 6 4 1 6E7189 y/8 7 4 1 6E7189 y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/4 13 5 1 6B4655 h/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l/16 1 2 2 c1 l/16 1 2 1 FFC94D l"
    private const val MOCHI_GLAD =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 9 1 1 2A2532 y/3 8 1 1 2A2532 y/4 9 1 1 2A2532 y/8 9 1 1 2A2532 y/9 8 1 1 2A2532 y/10 9 1 1 2A2532 y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l/14 1 2 2 FFE9A8 l/19 3 2 2 5EE0AC l"
    private const val MOCHI_SLEEPY =
        "13 14 3 3 6E7189 l/20 8 2 1 8C8FA8 t/22 5 2 3 8C8FA8 t/10 7 10 7 8C8FA8 b/11 6 8 1 8C8FA8 b/10 12 10 2 6E7189 b/10 12 10 1 4A4A5C b/12 13 1 1 c0 b/15 13 1 1 c1 b/18 13 1 1 c2 b/16 14 3 3 8C8FA8 l/1 4 11 10 8C8FA8 h/2 3 9 1 8C8FA8 h/0 6 1 6 8C8FA8 h/12 6 1 6 8C8FA8 h/2 0 1 1 8C8FA8 e/2 1 2 1 8C8FA8 e/2 2 3 1 8C8FA8 e/10 0 1 1 8C8FA8 e/9 1 2 1 8C8FA8 e/8 2 3 1 8C8FA8 e/2 9 3 1 2A2532 y/8 9 3 1 2A2532 y/2 8 3 1 6E7189 y/8 8 3 1 6E7189 y/0 11 2 2 A9AABF h/11 11 2 2 A9AABF h/5 11 3 1 E8A6B4 h/4 12 5 1 6B4655 h/3 14 3 3 8C8FA8 l/7 14 3 3 8C8FA8 l/14 2 4 1 7FE3C0 l/17 3 1 1 7FE3C0 l/16 4 1 1 7FE3C0 l/15 5 1 1 7FE3C0 l/14 6 4 1 7FE3C0 l"

    // ── Barley: dog, bones she buried ──
    private const val BARLEY_STAND =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l"
    private const val BARLEY_WALK =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l"
    private const val BARLEY_FRONT =
        "6 16 4 3 B5813A l/14 16 4 3 B5813A l/4 4 16 12 D9A05B l/5 3 14 1 D9A05B l/3 6 1 8 D9A05B l/20 6 1 8 D9A05B l/4 14 16 2 B5813A l/1 5 3 8 B5813A e/20 5 3 8 B5813A e/7 1 5 1 c0 l/7 2 5 1 c0 l/13 1 4 1 c1 l/13 2 4 1 c1 l/6 7 4 5 2E1C12 y/14 7 4 5 2E1C12 y/6 7 2 2 ffffff y/14 7 2 2 ffffff y/3 11 3 2 E8B67F l/18 11 3 2 E8B67F l/9 11 6 4 F0D3A8 l/10 11 4 2 4A3524 l"
    private const val BARLEY_CONTENT =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l"
    private const val BARLEY_EXPECT =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 6 3 5 2E1C12 y/8 6 3 5 2E1C12 y/2 6 2 2 ffffff y/8 6 2 2 ffffff y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l/15 0 1 1 FFE9A8 l/15 1 1 2 FFE9A8 l"
    private const val BARLEY_PECKISH =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 7 3 4 2E1C12 y/8 7 3 4 2E1C12 y/3 7 2 2 ffffff y/9 7 2 2 ffffff y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/5 12 3 2 4A3524 m/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l/15 1 3 1 5FD3A8 l/14 2 5 2 c0 l/15 4 3 1 1F7F5F l"
    private const val BARLEY_CAREFUL =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 8 3 3 2E1C12 y/8 8 3 3 2E1C12 y/2 8 1 1 ffffff y/8 8 1 1 ffffff y/1 6 4 1 B5813A y/8 7 4 1 B5813A y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/4 13 5 1 4A3524 h/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l/16 1 2 2 c1 l/16 1 2 1 FFC94D l"
    private const val BARLEY_GLAD =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 9 1 1 2E1C12 y/3 8 1 1 2E1C12 y/4 9 1 1 2E1C12 y/8 9 1 1 2E1C12 y/9 8 1 1 2E1C12 y/10 9 1 1 2E1C12 y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l/14 1 2 2 FFE9A8 l/19 3 2 2 5EE0AC l"
    private const val BARLEY_SLEEPY =
        "13 14 3 3 B5813A l/20 6 2 1 D9A05B t/21 4 2 2 D9A05B t/10 7 10 7 D9A05B b/11 6 8 1 D9A05B b/10 12 10 2 B5813A b/11 4 4 1 c0 b/11 5 4 1 c0 b/16 4 3 1 c1 b/16 5 3 1 c1 b/16 14 3 3 D9A05B l/1 4 11 10 D9A05B h/2 3 9 1 D9A05B h/0 6 1 6 D9A05B h/12 6 1 6 D9A05B h/0 4 2 7 B5813A e/11 4 2 7 B5813A e/0 10 2 1 4A3524 e/2 9 3 1 2E1C12 y/8 9 3 1 2E1C12 y/2 8 3 1 B5813A y/8 8 3 1 B5813A y/0 11 2 2 E8B67F h/11 11 2 2 E8B67F h/4 11 5 3 F0D3A8 h/5 11 3 2 4A3524 h/3 14 3 3 D9A05B l/7 14 3 3 D9A05B l/14 2 4 1 7FE3C0 l/17 3 1 1 7FE3C0 l/16 4 1 1 7FE3C0 l/15 5 1 1 7FE3C0 l/14 6 4 1 7FE3C0 l"

    /**
     * Every pose for one animal, keyed by [CompanionPose].
     *
     * Decoded on first access and cached, so the parse happens once per animal
     * the user actually looks at rather than for all six at startup.
     */
    private val decoded = mutableMapOf<String, List<Px>>()

    fun sprite(animal: CompanionAnimal, pose: CompanionPose): List<Px> {
        val key = animal.code + ":" + pose.name
        return decoded.getOrPut(key) { decode(raw(animal, pose)) }
    }

    private fun raw(animal: CompanionAnimal, pose: CompanionPose): String = when (animal) {
        CompanionAnimal.PENNY -> when (pose) {
            CompanionPose.STAND -> PENNY_STAND
            CompanionPose.WALK -> PENNY_WALK
            CompanionPose.FRONT -> PENNY_FRONT
            CompanionPose.CONTENT -> PENNY_CONTENT
            CompanionPose.EXPECT -> PENNY_EXPECT
            CompanionPose.PECKISH -> PENNY_PECKISH
            CompanionPose.CAREFUL -> PENNY_CAREFUL
            CompanionPose.GLAD -> PENNY_GLAD
            CompanionPose.SLEEPY -> PENNY_SLEEPY
        }
        CompanionAnimal.ACORN -> when (pose) {
            CompanionPose.STAND -> ACORN_STAND
            CompanionPose.WALK -> ACORN_WALK
            CompanionPose.FRONT -> ACORN_FRONT
            CompanionPose.CONTENT -> ACORN_CONTENT
            CompanionPose.EXPECT -> ACORN_EXPECT
            CompanionPose.PECKISH -> ACORN_PECKISH
            CompanionPose.CAREFUL -> ACORN_CAREFUL
            CompanionPose.GLAD -> ACORN_GLAD
            CompanionPose.SLEEPY -> ACORN_SLEEPY
        }
        CompanionAnimal.KUMO -> when (pose) {
            CompanionPose.STAND -> KUMO_STAND
            CompanionPose.WALK -> KUMO_WALK
            CompanionPose.FRONT -> KUMO_FRONT
            CompanionPose.CONTENT -> KUMO_CONTENT
            CompanionPose.EXPECT -> KUMO_EXPECT
            CompanionPose.PECKISH -> KUMO_PECKISH
            CompanionPose.CAREFUL -> KUMO_CAREFUL
            CompanionPose.GLAD -> KUMO_GLAD
            CompanionPose.SLEEPY -> KUMO_SLEEPY
        }
        CompanionAnimal.BISCUIT -> when (pose) {
            CompanionPose.STAND -> BISCUIT_STAND
            CompanionPose.WALK -> BISCUIT_WALK
            CompanionPose.FRONT -> BISCUIT_FRONT
            CompanionPose.CONTENT -> BISCUIT_CONTENT
            CompanionPose.EXPECT -> BISCUIT_EXPECT
            CompanionPose.PECKISH -> BISCUIT_PECKISH
            CompanionPose.CAREFUL -> BISCUIT_CAREFUL
            CompanionPose.GLAD -> BISCUIT_GLAD
            CompanionPose.SLEEPY -> BISCUIT_SLEEPY
        }
        CompanionAnimal.MOCHI -> when (pose) {
            CompanionPose.STAND -> MOCHI_STAND
            CompanionPose.WALK -> MOCHI_WALK
            CompanionPose.FRONT -> MOCHI_FRONT
            CompanionPose.CONTENT -> MOCHI_CONTENT
            CompanionPose.EXPECT -> MOCHI_EXPECT
            CompanionPose.PECKISH -> MOCHI_PECKISH
            CompanionPose.CAREFUL -> MOCHI_CAREFUL
            CompanionPose.GLAD -> MOCHI_GLAD
            CompanionPose.SLEEPY -> MOCHI_SLEEPY
        }
        CompanionAnimal.BARLEY -> when (pose) {
            CompanionPose.STAND -> BARLEY_STAND
            CompanionPose.WALK -> BARLEY_WALK
            CompanionPose.FRONT -> BARLEY_FRONT
            CompanionPose.CONTENT -> BARLEY_CONTENT
            CompanionPose.EXPECT -> BARLEY_EXPECT
            CompanionPose.PECKISH -> BARLEY_PECKISH
            CompanionPose.CAREFUL -> BARLEY_CAREFUL
            CompanionPose.GLAD -> BARLEY_GLAD
            CompanionPose.SLEEPY -> BARLEY_SLEEPY
        }
    }

    private fun decode(spec: String): List<Px> = spec.split('/').map { field ->
        val p = field.split(' ')
        val colour = p[4]
        Px(
            x = p[0].toInt(),
            y = p[1].toInt(),
            w = p[2].toInt(),
            h = p[3].toInt(),
            // `c0`..`c5` defer to the palette; anything else is the design's
            // own hex for this animal, which is its identity and not themed.
            currency = if (colour.startsWith("c")) colour.substring(1).toInt() else -1,
            argb = if (colour.startsWith("c")) 0L else 0xFF000000L or colour.toLong(16),
            part = p[5][0],
        )
    }
}

/** One rectangle of a sprite, in grid coordinates. */
internal class Px(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    /** Opaque ARGB, or 0 when [currency] decides the colour. */
    val argb: Long,
    val part: Char,
    /** Palette currency index, or -1 for a fixed colour. */
    val currency: Int,
)

/**
 * The poses the design ships: three bodies and six faces.
 *
 * 14a's summary is "ten sprites and a state machine — three poses, six faces,
 * one flip, plus the loop that decides which is showing". The flip is the
 * mirror, so it is a draw-time concern rather than a pose.
 */
enum class CompanionPose {
    /** Standing: breath, blink, tail. */
    STAND,

    /** Walking in three-quarter view, four legs, 8fps. */
    WALK,

    /** The front waddle: turned to address you, which is what she does at each end. */
    FRONT,

    // The six emotion faces, with the triggers 14a gives them.
    /** Nothing to do. */
    CONTENT,

    /** Log something. */
    EXPECT,

    /** Confirm drafts. */
    PECKISH,

    /** A pocket is low. */
    CAREFUL,

    /** You just did it. */
    GLAD,

    /** After hours. */
    SLEEPY,
}