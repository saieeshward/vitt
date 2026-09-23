package ie.shoonya.vitt.ui.theme

import androidx.compose.ui.graphics.Color
import ie.shoonya.vitt.theme.ThemeTokens

/**
 * The raw palette, from the identity design.
 *
 * The numbers themselves live in `:shared` as [ThemeTokens], where a JVM test
 * holds every theme to a contrast floor; this object only lifts them into
 * Compose's `Color`. Add a value there, not here, or the test cannot see it.
 *
 * Two rules govern every value and are enforced by how they are exposed in
 * [VittColors] rather than by discipline:
 *
 * 1. **A currency is a colour.** Hues are assigned in order from a fixed
 *    six-hue set, so EUR is the same green for every user and two currencies
 *    never collide.
 * 2. **Money is never coloured by sentiment.** There is no "red for overspent".
 *    Health runs accent-to-neutral, because a red ledger is a verdict and the
 *    tone rules forbid verdicts. The single red in the product is a destructive
 *    confirmation.
 */
internal object Palette {

    // Light — cream ground, ink text.
    val Cream = Color(ThemeTokens.Cream)
    val CreamSurface = Color(ThemeTokens.CreamSurface)
    val Ink = Color(ThemeTokens.Ink)
    val InkMuted = Color(ThemeTokens.InkMuted)
    val InkFaint = Color(ThemeTokens.InkFaint)
    val Violet = Color(ThemeTokens.Violet)
    val VioletWash = Color(ThemeTokens.VioletWash)

    // Dark — same hues, lifted.
    val Ground = Color(ThemeTokens.Ground)
    val GroundSurface = Color(ThemeTokens.GroundSurface)
    val Paper = Color(ThemeTokens.Paper)
    val PaperMuted = Color(ThemeTokens.PaperMuted)
    val PaperFaint = Color(ThemeTokens.PaperFaint)
    val VioletLifted = Color(ThemeTokens.VioletLifted)

    /**
     * The fixed six-hue currency set, in assignment order.
     *
     * Order is fixed rather than per-user so a screenshot means the same thing
     * to two people, and so a user adding a fourth currency does not have the
     * first three change colour underneath them.
     */
    val CurrencyLight = ThemeTokens.CurrencyLight.map { Color(it) }

    val CurrencyDark = ThemeTokens.CurrencyDark.map { Color(it) }

    val PipLight = Color(ThemeTokens.PipLight)
    val PipDark = Color(ThemeTokens.PipDark)

    /** Destructive confirmation only. Never a balance, never a budget state. */
    val Destructive = Color(ThemeTokens.Destructive)
}
