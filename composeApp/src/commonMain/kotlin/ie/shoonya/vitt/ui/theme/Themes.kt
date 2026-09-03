package ie.shoonya.vitt.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palettes the user can choose between, and the accents that go in them.
 *
 * **Curated, not a colour picker.** Two things here are load-bearing and free
 * colour input would break both. The six currency hues are fixed so that EUR is
 * the same green for every user and two currencies never collide, and the accent
 * marks *what is live* against them. A user who set their accent to the same
 * green as their first currency would have a screen where the two meanings are
 * indistinguishable, and one who set ink close to ground would have a screen
 * they cannot read. Neither is a state the app should be able to reach.
 *
 * So a theme is a whole vetted palette and an accent is one of a short list,
 * which also makes the stored value a short stable string rather than a set of
 * hex codes to migrate later.
 *
 * Worth noting where the idea came from: Revolut, the nearest thing to a
 * reference here, personalises card art and light/dark rather than exposing
 * arbitrary colour. The same conclusion from a much larger design team.
 */
enum class ThemeChoice(
    val code: String,
    val label: String,
    /** One line for the picker, saying what it is rather than selling it. */
    val note: String,
    val dark: Boolean,
) {
    /** The identity's own palette. Warm cream, ink text. */
    CREAM("cream", "Cream", "The original. Warm and quiet.", dark = false),

    /**
     * Plain white and near-black.
     *
     * Here for legibility rather than taste: cream on ink is a lower contrast
     * than white on black, and some people need the higher one. Keeping it as a
     * theme rather than a separate accessibility switch means it is somewhere
     * people will actually find it.
     */
    PAPER("paper", "Paper", "Higher contrast. Plain white.", dark = false),

    /** The identity's dark palette. */
    SLATE("slate", "Slate", "Dark, cool, low glare.", dark = true),

    /** A warm dark, for anyone who finds a cool one clinical. */
    DUSK("dusk", "Dusk", "Dark and warm.", dark = true),
    ;

    companion object {
        val DEFAULT = CREAM

        /**
         * Resolves a stored code, falling back to the default.
         *
         * Falls back rather than throwing, because the stored value may name a
         * theme a newer build has and this one does not. See
         * [ie.shoonya.vitt.model.Choice] for why the row itself is left alone.
         */
        fun ofCode(code: String?): ThemeChoice =
            entries.firstOrNull { it.code == code?.trim()?.lowercase() } ?: DEFAULT
    }
}

/**
 * The accent, which marks what is live or mine and never what is good.
 *
 * Green, amber and blue are deliberately **not** offered. They are the first
 * three currency hues, so they are the ones a user is most likely to have on
 * screen already, and an accent that matches a currency dot makes two different
 * meanings look like one thing.
 */
enum class AccentChoice(
    val code: String,
    val label: String,
    private val light: Color,
    private val lifted: Color,
) {
    VIOLET("violet", "Violet", Palette.Violet, Palette.VioletLifted),
    CLAY("clay", "Clay", Color(0xFFB8543A), Color(0xFFE0876A)),
    PLUM("plum", "Plum", Color(0xFF97417F), Color(0xFFCB7EB3)),
    ;

    /** The lifted variant on a dark ground, where the light one goes muddy. */
    fun colour(dark: Boolean): Color = if (dark) lifted else light

    companion object {
        val DEFAULT = VIOLET

        fun ofCode(code: String?): AccentChoice =
            entries.firstOrNull { it.code == code?.trim()?.lowercase() } ?: DEFAULT
    }
}

/**
 * Builds the semantic roles for a theme and accent.
 *
 * The currency hues are **not** a parameter: they come from the light or dark
 * set according to the theme's own ground, and no theme may replace them. That
 * is the rule this whole file exists to keep.
 */
internal fun ThemeChoice.colours(accent: AccentChoice): VittColors {
    val tint = accent.colour(dark)
    return when (this) {
        ThemeChoice.CREAM -> VittColors.light().copy(
            accent = tint,
            // Income and settled debts. Kept as the first currency hue rather
            // than derived from the accent, so it does not move when the accent
            // does: it means "money in", not "this is live".
            accentSoft = Palette.CurrencyLight[0],
        )

        ThemeChoice.PAPER -> VittColors.light().copy(
            ground = Color(0xFFFFFFFF),
            surface = Color(0xFFF4F4F6),
            card = Color(0xFFFFFFFF),
            // A hard shadow would be the only heavy thing on a high-contrast
            // screen, so it stays soft and the hairline does the separating.
            shadow = Color(0x14101014),
            ink = Color(0xFF101014),
            inkMuted = Color(0xFF4A4A55),
            inkFaint = Color(0xFF6E6E7A),
            hairline = Color(0xFF101014).copy(alpha = 0.14f),
            accent = tint,
            accentSoft = Palette.CurrencyLight[0],
        )

        ThemeChoice.SLATE -> VittColors.dark().copy(
            accent = tint,
            accentSoft = Palette.CurrencyDark[0],
        )

        ThemeChoice.DUSK -> VittColors.dark().copy(
            ground = Color(0xFF1B1517),
            surface = Color(0xFF2A2124),
            card = Color(0xFF2A2124),
            ink = Color(0xFFE8DCDA),
            inkMuted = Color(0xFFB4A3A2),
            inkFaint = Color(0xFF7C6C6C),
            hairline = Color(0xFFE8DCDA).copy(alpha = 0.09f),
            accent = tint,
            accentSoft = Palette.CurrencyDark[0],
        )
    }
}
