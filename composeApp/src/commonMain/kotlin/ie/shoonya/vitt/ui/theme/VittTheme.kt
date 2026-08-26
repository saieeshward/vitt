package ie.shoonya.vitt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * One rule shapes it: **one loud number per screen.** [moneyHero] is the only
 * large size in the system, and everything else sits at 13–15sp. Hierarchy comes
 * from size and space, not weight, so there is no bold body style to reach for.
 */
@Immutable
data class VittType(
    /** The single 34sp figure a screen is allowed. Tabular. */
    val moneyHero: TextStyle,
    /** Amounts in lists. Tabular, so columns align down the page. */
    val money: TextStyle,
    /** Screen-leading headings: 30sp bold, tight. The design's own scale. */
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    /** Uppercase, tracked. Section headers and provenance. */
    val caption: TextStyle,
) {
    companion object {
        /**
         * Tabular figures are not cosmetic. Proportional digits make a column of
         * amounts ragged, and make the keypad display shift sideways as each
         * digit is typed — the most-used screen in the app.
         */
        private const val TABULAR = "tnum"

        fun default(): VittType {
            val sans = FontFamily.Default
            return VittType(
                // Weight and tracking come from the design's own `.lnum` rule:
                // semibold at -0.035em. Medium with looser tracking read as
                // limp next to the mockups.
                moneyHero = TextStyle(
                    fontFamily = sans, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.2).sp, textAlign = TextAlign.Start,
                    fontFeatureSettings = TABULAR,
                ),
                money = TextStyle(
                    fontFamily = sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR,
                ),
                display = TextStyle(
                    fontFamily = sans, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.05).sp, lineHeight = 34.sp,
                ),
                title = TextStyle(fontFamily = sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                body = TextStyle(
                    fontFamily = sans, fontSize = 15.sp, fontWeight = FontWeight.Normal,
                    lineHeight = 22.sp,
                ),
                label = TextStyle(fontFamily = sans, fontSize = 12.sp, fontWeight = FontWeight.Normal),
                caption = TextStyle(
                    fontFamily = sans, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                ),
            )
        }
    }
}

/** Compact spacing scale — dense lists are a stated goal, not an accident. */
/**
 * Corner radii, from the design's own classes.
 *
 * `.soft` cards are 22px and `.bigkey` keypad keys are 16px — noticeably rounder
 * than the 14/8 an earlier pass guessed at, which is most of why the screens
 * read as generic.
 */
@Immutable
data class VittRadius(
    val card: Dp = 20.dp,
    val key: Dp = 16.dp,
    val tile: Dp = 18.dp,
    val pill: Dp = 10.dp,
)

@Immutable
data class VittSpacing(
    val hair: Dp = 2.dp,
    val tight: Dp = 6.dp,
    val snug: Dp = 8.dp,
    val base: Dp = 12.dp,
    val loose: Dp = 16.dp,
    val section: Dp = 24.dp,
)

val LocalVittColors = staticCompositionLocalOf { VittColors.light() }
val LocalVittType = staticCompositionLocalOf { VittType.default() }
val LocalVittSpacing = staticCompositionLocalOf { VittSpacing() }
val LocalVittRadius = staticCompositionLocalOf { VittRadius() }

object Vitt {
    val colors: VittColors
        @Composable @ReadOnlyComposable get() = LocalVittColors.current
    val type: VittType
        @Composable @ReadOnlyComposable get() = LocalVittType.current
    val space: VittSpacing
        @Composable @ReadOnlyComposable get() = LocalVittSpacing.current
    val radius: VittRadius
        @Composable @ReadOnlyComposable get() = LocalVittRadius.current
}

/**
 * Follows the system theme in both directions.
 *
 * Material's scheme is populated too, so stock Material components inherit the
 * palette rather than appearing in default purple next to themed ones — but
 * VITT's own surfaces read [Vitt] directly, because Material has no role for
 * "this currency" or "how much room is left".
 */
@Composable
fun VittTheme(
    /**
     * Light by default, regardless of the system setting.
     *
     * The identity's primary palette is the cream one — warm and quiet is the
     * intended feel, and a personal tool should not inherit an OS-wide
     * preference set for reading in bed. A phone in dark mode was otherwise
     * showing the app's secondary palette as if it were the design.
     */
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) VittColors.dark() else VittColors.light()
    val material = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.ground,
            surface = colors.surface,
            onBackground = colors.ink,
            onSurface = colors.ink,
            onSurfaceVariant = colors.inkMuted,
            error = colors.destructive,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.ground,
            surface = colors.surface,
            onBackground = colors.ink,
            onSurface = colors.ink,
            onSurfaceVariant = colors.inkMuted,
            error = colors.destructive,
        )
    }

    CompositionLocalProvider(
        LocalVittColors provides colors,
        LocalVittType provides VittType.default(),
        LocalVittSpacing provides VittSpacing(),
        LocalVittRadius provides VittRadius(),
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
