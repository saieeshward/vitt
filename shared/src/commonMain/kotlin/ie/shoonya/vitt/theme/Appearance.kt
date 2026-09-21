package ie.shoonya.vitt.theme

/**
 * Whether the app is light, dark, or whichever the phone currently is.
 *
 * This is deliberately *not* the same question as which palette. The app has
 * eight palettes, four light and four dark, and an earlier revision let the
 * palette alone decide: picking Ink was how you got a dark app. That made the
 * phone's own Light/Dark switch do nothing, which reads as a bug rather than as
 * a decision, and it left no way to say "dark at night, light in the day".
 *
 * Splitting it in two resolves that without the conflict `VittTheme` warned
 * about. Appearance decides light or dark; the palette choice decides *which*
 * light one and *which* dark one, kept separately so switching sides does not
 * overwrite the other side's pick. Two settings, two distinct questions, and
 * nothing is deciding the same thing twice.
 */
enum class Appearance(val code: String, val label: String) {
    /** Follow the phone. The default, because it is what a phone user expects. */
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    /** Resolves to a side. [systemDark] is what the OS reports right now. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT = SYSTEM

        /**
         * Resolves a stored code, falling back to the default.
         *
         * Falls back rather than throwing, for the reason
         * `ie.shoonya.vitt.model.Choice` gives: the row may name an option a
         * newer build has and this one does not, and it is kept either way.
         */
        fun ofCode(code: String?): Appearance =
            entries.firstOrNull { it.code == code?.trim()?.lowercase() } ?: DEFAULT

        /**
         * What to assume for someone upgrading from a build that had no
         * appearance setting.
         *
         * They chose a palette, and if it was a dark one they chose a dark app.
         * Defaulting them to System would turn their app light on a phone in
         * light mode, which is the app throwing away a decision they made. So
         * an unset appearance with a dark palette stored means Dark, and only a
         * genuinely absent history means System.
         */
        fun migrated(stored: String?, storedPaletteIsDark: Boolean): Appearance =
            if (stored != null) ofCode(stored)
            else if (storedPaletteIsDark) DARK
            else DEFAULT
    }
}
