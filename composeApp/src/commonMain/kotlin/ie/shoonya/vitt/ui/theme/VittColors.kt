package ie.shoonya.vitt.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour roles.
 *
 * Deliberately narrow: there is no `error` role for money and no `success`
 * role at all. Code that wants to paint an amount red has to reach for
 * [destructive], which is named to make that read wrong in review.
 */
@Immutable
data class VittColors(
    val ground: Color,
    val surface: Color,
    /**
     * Cards, which are white on the cream ground rather than a tint of it.
     *
     * This is most of what separates the design's look from a flat one: a card
     * one shade off the background reads as muddy, whereas white plus a soft
     * shadow reads as a card sitting on paper.
     */
    val card: Color,
    /** The card shadow colour, at the design's own low opacity. */
    val shadow: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Absent data and placeholders — never information the user must read. */
    val inkFaint: Color,
    /** Live, mine, now. Marks what is current, never what is good. */
    val accent: Color,
    /** Income, settled, received. */
    val accentSoft: Color,
    val hairline: Color,
    val pip: Color,
    val destructive: Color,
    private val currencyHues: List<Color>,
    val isDark: Boolean,
) {
    /**
     * The colour for a currency, by its assignment order in the user's ledger.
     *
     * Wraps past six rather than generating new hues: a seventh currency
     * repeating a colour is better than an unvetted hue that fails contrast or
     * collides with the accent.
     */
    fun currency(index: Int): Color = currencyHues[index.mod(currencyHues.size)]

    /**
     * Budget health, from comfortable (0f) to over (1f).
     *
     * Interpolates accent → neutral, never green → red. Over budget is the
     * *absence* of accent — the ledger goes quiet rather than shouting.
     *
     * The neutral end is `inkFaint`, not `inkMuted`. Fading to `inkMuted` made
     * an over-budget bar a solid dark line at full width — the single heaviest
     * mark on the screen, which is this rule stood on its head. The line above
     * the bar already states the overage in words, so the bar owns no fact of
     * its own and has nothing to be loud about.
     */
    fun health(pressure: Float): Color {
        val t = pressure.coerceIn(0f, 1f)
        return Color(
            red = accent.red + (inkFaint.red - accent.red) * t,
            green = accent.green + (inkFaint.green - accent.green) * t,
            blue = accent.blue + (inkFaint.blue - accent.blue) * t,
            alpha = 1f,
        )
    }

    companion object {
        fun light() = VittColors(
            ground = Palette.Cream,
            surface = Palette.CreamSurface,
            card = Color(0xFFFFFFFF),
            shadow = Color(0x0F241F33),
            ink = Palette.Ink,
            inkMuted = Palette.InkMuted,
            inkFaint = Palette.InkFaint,
            accent = Palette.Violet,
            accentSoft = Palette.CurrencyLight[0],
            hairline = Palette.Ink.copy(alpha = 0.10f),
            pip = Palette.PipLight,
            destructive = Palette.Destructive,
            currencyHues = Palette.CurrencyLight,
            isDark = false,
        )

        fun dark() = VittColors(
            ground = Palette.Ground,
            surface = Palette.GroundSurface,
            // On a dark ground a white card would glare, so elevation comes
            // from a lifted surface rather than from light.
            card = Palette.GroundSurface,
            shadow = Color(0x66000000),
            ink = Palette.Paper,
            inkMuted = Palette.PaperMuted,
            inkFaint = Palette.PaperFaint,
            accent = Palette.VioletLifted,
            accentSoft = Palette.CurrencyDark[0],
            hairline = Palette.Paper.copy(alpha = 0.09f),
            pip = Palette.PipDark,
            destructive = Palette.Destructive,
            currencyHues = Palette.CurrencyDark,
            isDark = true,
        )
    }
}
