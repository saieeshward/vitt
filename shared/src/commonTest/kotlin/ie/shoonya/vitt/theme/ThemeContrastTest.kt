package ie.shoonya.vitt.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds every theme to a contrast floor.
 *
 * Body text (ink, inkMuted) needs WCAG AA's 4.5:1. Placeholders, currency
 * strokes and the accent are graphical or large enough for 3:1; a chart line
 * under that is invisible on some panels, which for a currency-coloured chart
 * means a whole currency disappears.
 */
class ThemeContrastTest {

    private val body = 4.5
    private val graphic = 3.0

    private fun check(what: String, fg: Long, bg: Long, floor: Double) {
        val r = Contrast.ratio(fg, bg)
        assertTrue(r >= floor, "$what is ${(r * 100).toInt() / 100.0}:1, needs $floor:1")
    }

    @Test
    fun `luminance and ratio match the WCAG reference points`() {
        assertEquals(1.0, Contrast.luminance(0xFFFFFFFF), 1e-9)
        assertEquals(0.0, Contrast.luminance(0xFF000000), 1e-9)
        assertEquals(21.0, Contrast.ratio(0xFF000000, 0xFFFFFFFF), 1e-9)
        assertEquals(1.0, Contrast.ratio(0xFF6B5BFF, 0xFF6B5BFF), 1e-9)
    }

    @Test
    fun `ink and muted ink read as body text on ground and surface in every theme`() {
        for (t in ThemeTokens.Themes) {
            for ((name, bg) in listOf("ground" to t.ground, "surface" to t.surface)) {
                check("${t.code} ink on $name", t.ink, bg, body)
                check("${t.code} inkMuted on $name", t.inkMuted, bg, body)
            }
        }
    }

    @Test
    fun `faint ink is still visible on ground and surface in every theme`() {
        for (t in ThemeTokens.Themes) {
            check("${t.code} inkFaint on ground", t.inkFaint, t.ground, graphic)
            check("${t.code} inkFaint on surface", t.inkFaint, t.surface, graphic)
        }
    }

    @Test
    fun `every currency stroke clears the ground in every theme`() {
        for (t in ThemeTokens.Themes) {
            val hues = if (t.dark) ThemeTokens.CurrencyDark else ThemeTokens.CurrencyLight
            hues.forEachIndexed { i, hue ->
                check("${t.code} currency ${i + 1} on ground", hue, t.ground, graphic)
            }
        }
    }

    @Test
    fun `every accent clears the ground in every theme`() {
        for (t in ThemeTokens.Themes) {
            for (a in ThemeTokens.Accents) {
                val tint = if (t.dark) a.lifted else a.light
                check("${t.code} accent ${a.code} on ground", tint, t.ground, graphic)
            }
        }
    }

    @Test
    fun `destructive clears the surface in every theme`() {
        for (t in ThemeTokens.Themes) {
            check("${t.code} destructive on surface", ThemeTokens.Destructive, t.surface, graphic)
        }
    }

    @Test
    fun `theme codes are unique and resolvable`() {
        assertEquals(ThemeTokens.Themes.size, ThemeTokens.Themes.map { it.code }.toSet().size)
        assertEquals("ink", ThemeTokens.theme("ink").code)
    }
}
