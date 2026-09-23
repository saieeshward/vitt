package ie.shoonya.vitt.theme

/**
 * The raw colour values behind every theme, as ARGB.
 *
 * They live in `:shared` rather than next to the Compose code that paints them
 * so that a plain JVM test can hold them to a contrast floor. The UI module has
 * no test source set and its `Color` type needs a Compose runtime, so a check
 * living there would either not run or would have to copy the values and drift.
 * Here the test reads the same constants the app compiles in.
 *
 * Nothing in this file knows what a colour is *for*; the roles, the rule that
 * no theme may replace the currency hues, and the accent list all stay in the
 * UI module. This is only the numbers.
 */
object ThemeTokens {

    /** Ground, surface and the three ink weights for one theme. */
    data class Theme(
        val code: String,
        val ground: Long,
        val surface: Long,
        val ink: Long,
        val inkMuted: Long,
        val inkFaint: Long,
        val dark: Boolean,
    )

    // Light — cream ground, ink text.
    const val Cream = 0xFFFFFDF7
    const val CreamSurface = 0xFFF2EFE7
    const val Ink = 0xFF241F33
    const val InkMuted = 0xFF6A6480
    const val InkFaint = 0xFF8A85A0
    const val Violet = 0xFF6B5BFF
    const val VioletWash = 0xFFF3F0FF

    // Dark — same hues, lifted.
    const val Ground = 0xFF17141F
    const val GroundSurface = 0xFF241F33
    const val Paper = 0xFFDAD5E6
    const val PaperMuted = 0xFFA7A1BC
    // Was 6A6480, the light theme's muted ink reused as the dark faint. On the
    // lifted surface it reached 2.84:1, under the 3:1 a placeholder still needs
    // to be seen at all. Lifted by the least that clears it.
    const val PaperFaint = 0xFF6F6886
    const val VioletLifted = 0xFF9C8DFF

    /**
     * The fixed six-hue currency set for light grounds, in assignment order.
     *
     * These sit a step below the identity document's hues (`35B98A`, `E39A12`,
     * `4C9DF7`, `EE6E9C`, `17A2A2`). The originals were chosen against cream and
     * measured between 2.1:1 and 2.9:1 on the light grounds, where a currency
     * stroke a pixel or two wide in a chart simply vanished. Each was darkened
     * along its own hue until it clears 3:1 on the darkest light ground (Linen),
     * so the hue a user learns as "euro" is unchanged; only its weight is. Violet
     * already passed and is untouched.
     */
    val CurrencyLight = listOf(
        0xFF2D9D75, // 1 — green
        0xFFBB7F0F, // 2 — amber
        0xFF2C8CF6, // 3 — blue
        0xFFEB558B, // 4 — pink
        0xFF6B5BFF, // 5 — violet
        0xFF169B9B, // 6 — teal
    )

    val CurrencyDark = listOf(
        0xFF5EE0AC,
        0xFFFFC94D,
        0xFF78BBFF,
        0xFFD95C88,
        0xFF9C8DFF,
        0xFF4FD1D1,
    )

    const val PipLight = 0xFFEE6E9C
    const val PipDark = 0xFFD95C88

    /** Destructive confirmation only. Never a balance, never a budget state. */
    const val Destructive = 0xFFD0453B

    /** Accent pairs: the light-ground value and the lifted one for dark grounds. */
    data class Accent(val code: String, val light: Long, val lifted: Long)

    val Accents = listOf(
        Accent("violet", Violet, VioletLifted),
        Accent("clay", 0xFFB8543A, 0xFFE0876A),
        Accent("plum", 0xFF97417F, 0xFFCB7EB3),
        Accent("indigo", 0xFF3D4DB7, 0xFF8F9CFF),
        Accent("wine", 0xFF8A2C45, 0xFFD06A84),
        Accent("steel", 0xFF4A6785, 0xFF93B1CF),
        Accent("coral", 0xFFE0563F, 0xFFFF8A72),
        Accent("mono", Ink, Paper),
    )

    val Themes = listOf(
        Theme("cream", Cream, CreamSurface, Ink, InkMuted, InkFaint, dark = false),
        Theme("paper", 0xFFFFFFFF, 0xFFF4F4F6, 0xFF101014, 0xFF4A4A55, 0xFF6E6E7A, dark = false),
        Theme("slate", Ground, GroundSurface, Paper, PaperMuted, PaperFaint, dark = true),
        Theme("dusk", 0xFF1B1517, 0xFF2A2124, 0xFFE8DCDA, 0xFFB4A3A2, 0xFF7C6C6C, dark = true),
        // Muted was 6E6862 (4.42:1 on the surface) and faint 908A83 (2.74:1);
        // both nudged one step darker. Linen's surface is the darkest of the
        // light themes, so its inks have the least room of the four.
        Theme("linen", 0xFFF3F1EE, 0xFFE9E6E1, 0xFF2A2724, 0xFF6B6560, 0xFF89827B, dark = false),
        // Faint was 87909E, 2.72:1 on the surface.
        Theme("mist", 0xFFF4F6F9, 0xFFE8ECF2, 0xFF1E2430, 0xFF5E6878, 0xFF7F8897, dark = false),
        Theme("ink", 0xFF000000, 0xFF141418, 0xFFECEAF2, 0xFFA8A5B5, 0xFF6C6978, dark = true),
        Theme("moss", 0xFF141A16, 0xFF1F2822, 0xFFDDE6DF, 0xFFA3B2A8, 0xFF6C7A71, dark = true),
    )

    fun theme(code: String): Theme = Themes.first { it.code == code }
}
