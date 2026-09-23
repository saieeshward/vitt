package ie.shoonya.vitt.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowLayoutTest {

    @Test
    fun `a phone upright is the phone layout`() {
        val phone = WindowLayout(390, 844)
        assertEquals(WindowLayout.Width.COMPACT, phone.width)
        assertFalse(phone.useRail)
        assertFalse(phone.dialogSheets)
        assertNull(phone.maxContentWidthDp)
    }

    @Test
    fun `a phone on its side gets the rail and keeps its sheets`() {
        // Height is what a landscape phone has least of, so the bottom bar goes.
        val side = WindowLayout(844, 390)
        assertTrue(side.useRail)
        assertFalse(side.dialogSheets)
    }

    @Test
    fun `an iPad gets the rail — dialogs and a readable width`() {
        val ipad = WindowLayout(1024, 1366)
        assertEquals(WindowLayout.Width.EXPANDED, ipad.width)
        assertTrue(ipad.useRail)
        assertTrue(ipad.dialogSheets)
        assertEquals(WindowLayout.READABLE_WIDTH, ipad.maxContentWidthDp)
    }

    @Test
    fun `an iPad in Split View at a third is a phone`() {
        // The window decides, never the device.
        assertFalse(WindowLayout(320, 1366).useRail)
    }

    @Test
    fun `the breakpoints fall where Material puts them`() {
        assertEquals(WindowLayout.Width.COMPACT, WindowLayout(599, 900).width)
        assertEquals(WindowLayout.Width.MEDIUM, WindowLayout(600, 900).width)
        assertEquals(WindowLayout.Width.MEDIUM, WindowLayout(839, 900).width)
        assertEquals(WindowLayout.Width.EXPANDED, WindowLayout(840, 900).width)
        assertTrue(WindowLayout(700, 479).shortHeight)
        assertFalse(WindowLayout(700, 480).shortHeight)
    }
}
