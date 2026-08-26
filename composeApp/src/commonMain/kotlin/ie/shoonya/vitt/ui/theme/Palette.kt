package ie.shoonya.vitt.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw palette, from the identity design.
 *
 * Two rules govern every value here and are enforced by how they are exposed in
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
    val Cream = Color(0xFFFFFDF7)
    val CreamSurface = Color(0xFFF2EFE7)
    val Ink = Color(0xFF241F33)
    val InkMuted = Color(0xFF6A6480)
    val InkFaint = Color(0xFF8A85A0)
    val Violet = Color(0xFF6B5BFF)
    val VioletWash = Color(0xFFF3F0FF)

    // Dark — same hues, lifted.
    val Ground = Color(0xFF17141F)
    val GroundSurface = Color(0xFF241F33)
    val Paper = Color(0xFFDAD5E6)
    val PaperMuted = Color(0xFFA7A1BC)
    val PaperFaint = Color(0xFF6A6480)
    val VioletLifted = Color(0xFF9C8DFF)

    /**
     * The fixed six-hue currency set, in assignment order.
     *
     * Order is fixed rather than per-user so a screenshot means the same thing
     * to two people, and so a user adding a fourth currency does not have the
     * first three change colour underneath them.
     */
    val CurrencyLight = listOf(
        Color(0xFF35B98A), // 1 — green
        Color(0xFFE39A12), // 2 — amber
        Color(0xFF4C9DF7), // 3 — blue
        Color(0xFFEE6E9C), // 4 — pink
        Color(0xFF6B5BFF), // 5 — violet
        Color(0xFF17A2A2), // 6 — teal
    )

    val CurrencyDark = listOf(
        Color(0xFF5EE0AC),
        Color(0xFFFFC94D),
        Color(0xFF78BBFF),
        Color(0xFFD95C88),
        Color(0xFF9C8DFF),
        Color(0xFF4FD1D1),
    )

    val PipLight = Color(0xFFEE6E9C)
    val PipDark = Color(0xFFD95C88)

    /** Destructive confirmation only. Never a balance, never a budget state. */
    val Destructive = Color(0xFFD0453B)
}
