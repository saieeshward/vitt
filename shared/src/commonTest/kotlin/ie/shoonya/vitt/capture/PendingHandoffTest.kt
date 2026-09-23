package ie.shoonya.vitt.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingHandoffTest {

    @Test
    fun `a value offered before anyone listens is replayed on registration`() {
        // The cold-launch race: a widget tap starts the app, so the payload
        // lands before any UI exists. Dropping it is the bug where the widget
        // opens the app and nothing happens.
        val handoff = PendingHandoff<String>()
        handoff.offer("TESCO 12.50")

        val seen = mutableListOf<String>()
        handoff.listener = { seen += it }

        assertEquals(listOf("TESCO 12.50"), seen)
    }

    @Test
    fun `a value offered while listening goes straight through`() {
        val handoff = PendingHandoff<String>()
        val seen = mutableListOf<String>()
        handoff.listener = { seen += it }

        handoff.offer("SPAR 3.20")

        assertEquals(listOf("SPAR 3.20"), seen)
    }

    @Test
    fun `delivery is one-shot — a recreated UI does not reopen an old share`() {
        // Replaying on every registration would reopen a sheet the user has
        // already dismissed, every time the screen is recreated.
        val handoff = PendingHandoff<String>()
        handoff.offer("once")

        val first = mutableListOf<String>()
        handoff.listener = { first += it }
        val second = mutableListOf<String>()
        handoff.listener = { second += it }

        assertEquals(listOf("once"), first)
        assertEquals(emptyList(), second)
        assertNull(handoff.pending)
    }

    @Test
    fun `the held value is cleared before the listener runs`() {
        // A listener that re-enters synchronously must not find the same value
        // still sitting there and deliver it twice.
        val handoff = PendingHandoff<String>()
        handoff.offer("x")
        var seenPendingDuringCallback: String? = "not run"
        handoff.listener = { seenPendingDuringCallback = handoff.pending }
        assertNull(seenPendingDuringCallback)
    }

    @Test
    fun `clearing the listener holds the next value rather than losing it`() {
        val handoff = PendingHandoff<String>()
        handoff.listener = {}
        handoff.listener = null
        handoff.offer("held")
        assertEquals("held", handoff.pending)
    }

    @Test
    fun `only the most recent offer survives the wait`() {
        // Two shares before the app is up is two sheets nobody asked for. The
        // last one is what the person most recently meant.
        val handoff = PendingHandoff<String>()
        handoff.offer("first")
        handoff.offer("second")
        val seen = mutableListOf<String>()
        handoff.listener = { seen += it }
        assertEquals(listOf("second"), seen)
    }
}
