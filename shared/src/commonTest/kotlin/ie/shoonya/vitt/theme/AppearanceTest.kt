package ie.shoonya.vitt.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceTest {

    @Test
    fun `system follows the phone and the other two ignore it`() {
        assertTrue(Appearance.SYSTEM.isDark(systemDark = true))
        assertFalse(Appearance.SYSTEM.isDark(systemDark = false))

        // The point of picking a side is that the phone stops deciding.
        assertFalse(Appearance.LIGHT.isDark(systemDark = true))
        assertTrue(Appearance.DARK.isDark(systemDark = false))
    }

    @Test
    fun `the default is to follow the phone`() {
        assertEquals(Appearance.SYSTEM, Appearance.DEFAULT)
        assertEquals(Appearance.SYSTEM, Appearance.ofCode(null))
    }

    @Test
    fun `an unknown code falls back instead of throwing`() {
        // A newer build may store an option this one does not have, and the row
        // is kept rather than rewritten.
        assertEquals(Appearance.SYSTEM, Appearance.ofCode("auto-oled"))
        assertEquals(Appearance.DARK, Appearance.ofCode("  DARK "))
    }

    @Test
    fun `upgrading from a dark palette keeps the app dark`() {
        // Someone who picked Ink chose a dark app. Moving them to System would
        // turn it light on a light phone, discarding a decision they made.
        assertEquals(
            Appearance.DARK,
            Appearance.migrated(stored = null, storedPaletteIsDark = true),
        )
        assertEquals(
            Appearance.SYSTEM,
            Appearance.migrated(stored = null, storedPaletteIsDark = false),
        )
    }

    @Test
    fun `an explicit setting always wins over the migration guess`() {
        assertEquals(
            Appearance.LIGHT,
            Appearance.migrated(stored = "light", storedPaletteIsDark = true),
        )
    }
}
