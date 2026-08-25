package ie.shoonya.vitt.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HlcTest {

    private val nodeA = "a219e7a71cc18912"
    private val nodeB = "b0f1c2d3e4f50617"

    private fun clock(node: String, vararg times: Long): HlcClock {
        var i = 0
        return HlcClock(nodeId = node, now = { times[minOf(i++, times.size - 1)] })
    }

    @Test
    fun `encoding is fixed width and round trips`() {
        val hlc = Hlc(1_787_000_000_000L, 7, nodeA)
        val encoded = hlc.encode()
        assertEquals(Hlc.ENCODED_LENGTH, encoded.length)
        assertEquals(hlc, Hlc.decode(encoded))
    }

    @Test
    fun `encoding is lexicographically ordered - the property the sheet relies on`() {
        // The spreadsheet sorts these as plain text, so string order must equal
        // logical order for every field. If this breaks, folding the log yields
        // the wrong current state.
        val samples = listOf(
            Hlc(1_000L, 0, nodeA),
            Hlc(1_000L, 1, nodeA),
            Hlc(1_000L, 1, nodeB),
            Hlc(1_001L, 0, nodeA),
            Hlc(1_787_000_000_000L, 0, nodeA),
        )
        val byCompare = samples.sorted()
        val byString = samples.sortedBy { it.encode() }
        assertEquals(byCompare, byString)
    }

    @Test
    fun `counter increments when physical time has not moved`() {
        val c = clock(nodeA, 5_000L, 5_000L, 5_000L)
        assertEquals(0, c.issue().counter)
        assertEquals(1, c.issue().counter)
        assertEquals(2, c.issue().counter)
    }

    @Test
    fun `counter resets when physical time advances`() {
        val c = clock(nodeA, 5_000L, 5_000L, 6_000L)
        c.issue(); c.issue()
        val third = c.issue()
        assertEquals(6_000L, third.physicalMillis)
        assertEquals(0, third.counter)
    }

    @Test
    fun `physical time never goes backwards even if the device clock does`() {
        // NTP correction, or the user changing the date manually.
        val c = clock(nodeA, 10_000L, 9_000L)
        val first = c.issue()
        val second = c.issue()
        assertTrue(second > first, "clock must be monotonic despite the wall clock")
        assertEquals(first.physicalMillis, second.physicalMillis)
    }

    @Test
    fun `observing a remote event pulls this clock forward`() {
        // This is what stops a device that was offline from ordering its later
        // edits *before* edits it has already seen.
        val c = clock(nodeA, 1_000L)
        val remote = Hlc(50_000L, 3, nodeB)
        val merged = c.observe(remote)
        assertEquals(50_000L, merged.physicalMillis)
        assertEquals(4, merged.counter)
        assertTrue(merged > remote)
    }

    @Test
    fun `a week offline still merges in causal order`() {
        val week = 7 * 24 * 60 * 60 * 1000L
        // Phone A edits offline at t=0 and syncs a week later.
        val a = clock(nodeA, 0L)
        val oldEdit = a.issue()
        // Phone B edits yesterday, having never seen A.
        val b = clock(nodeB, week - 86_400_000L)
        val newerEdit = b.issue()

        assertTrue(newerEdit > oldEdit, "the later real-world edit must win")

        // After A observes B, A's next edit sorts after both.
        val a2 = HlcClock(nodeA, now = { week })
        a2.observe(newerEdit)
        assertTrue(a2.issue() > newerEdit)
    }

    @Test
    fun `rejects a wildly wrong remote clock`() {
        val c = HlcClock(nodeA, now = { 1_000L })
        val fromTheFuture = Hlc(1_000L + 10 * 60 * 1000L, 0, nodeB)
        assertFailsWith<ClockDriftException> { c.observe(fromTheFuture) }
    }

    @Test
    fun `two devices at the identical instant still order deterministically`() {
        // Without a stable tiebreak, last-write-wins would depend on read order,
        // so two devices could fold the same log into different states.
        val fromA = Hlc(9_000L, 0, nodeA)
        val fromB = Hlc(9_000L, 0, nodeB)
        assertTrue(fromA < fromB)
        assertEquals(fromA < fromB, fromA.encode() < fromB.encode())
    }

    @Test
    fun `iso formatting is exact`() {
        assertEquals("1970-01-01T00:00:00.000Z", Iso8601.format(0))
        assertEquals("2026-08-25T22:23:42.123Z", Iso8601.format(Iso8601.parse("2026-08-25T22:23:42.123Z")))
        // Leap day, and a leap-century boundary.
        assertEquals("2024-02-29T12:00:00.000Z", Iso8601.format(Iso8601.parse("2024-02-29T12:00:00.000Z")))
        assertEquals("2000-02-29T00:00:00.000Z", Iso8601.format(Iso8601.parse("2000-02-29T00:00:00.000Z")))
    }
}
