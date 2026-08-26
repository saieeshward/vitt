package ie.shoonya.vitt.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One malformed row must not cost the batch it travelled in.
 *
 * Sheets' append is all-or-nothing, so a single over-long cell fails 500 events
 * at once. Poisoning all of them contradicts the rule that nothing is ever
 * silently dropped — and `nextBatch` never selects POISON again, so those
 * records would be gone for good.
 */
class BatchFailureTest {

    private val nodeA = "a219e7a71cc18912"

    private fun events(n: Int): List<Event> {
        val clock = HlcClock(nodeA, now = { 1_000L })
        return (1..n).map {
            Event(clock.issue(), "transaction", "txn-$it", "amount", TaggedValue.Num(-it * 100L))
        }
    }

    @Test
    fun `isolates a single offender out of many`() = runTest {
        val all = events(16)
        val bad = all[9]
        var sends = 0
        val offenders = Bisect.failing(all) { batch ->
            sends++
            bad !in batch
        }
        assertEquals(listOf(bad), offenders)
        // Binary search, not one send per event.
        assertTrue(sends < all.size, "took $sends sends for ${all.size} events")
    }

    @Test
    fun `isolates several offenders`() = runTest {
        val all = events(16)
        val bad = setOf(all[2], all[11])
        val offenders = Bisect.failing(all) { batch -> batch.none { it in bad } }
        assertEquals(bad, offenders.toSet())
    }

    @Test
    fun `a transient failure resolves to no offender`() = runTest {
        // The first attempt failed for a reason that has passed. Nothing should
        // be blamed, and nothing poisoned.
        val all = events(8)
        var first = true
        val offenders = Bisect.failing(all) { if (first) { first = false; false } else true }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `every event failing blames every event`() = runTest {
        val all = events(4)
        assertEquals(all.toSet(), Bisect.failing(all) { false }.toSet())
    }

    @Test
    fun `only the offender is poisoned - the rest go back to the queue`() {
        val store = EventStore(testDriver())
        val all = events(8)
        all.forEach { store.append(it, 1L) }
        val batch = store.nextBatch()
        store.markInFlight(batch)

        val offender = batch[3]
        store.poisonOnly(listOf(offender), batch - offender, "400 cell too long")

        assertEquals(listOf(offender.hlc.encode()), store.poisoned())
        assertEquals(
            7,
            store.nextBatch().size,
            "the seven innocent events must remain sendable",
        )
    }

    @Test
    fun `poisoned entries can be returned to the queue`() {
        // A row rejected for an over-long note becomes sendable once the note is
        // shortened; poison must not be permanent.
        val store = EventStore(testDriver())
        val all = events(3)
        all.forEach { store.append(it, 1L) }
        val batch = store.nextBatch()
        store.markInFlight(batch)
        store.poisonOnly(batch, emptyList(), "400")

        assertEquals(3, store.poisoned().size)
        assertEquals(3, store.retryPoisoned())
        assertEquals(0, store.poisoned().size)
        assertEquals(3, store.nextBatch().size)
    }

    @Test
    fun `attempts still accumulate after reconcile moves an entry off in-flight`() {
        // Previously attempts were read only from the IN_FLIGHT list, so an
        // entry reconcile had already returned to PENDING counted from zero
        // every time and could never reach POISON however often it failed.
        val store = EventStore(testDriver())
        val one = events(1)
        store.append(one.first(), 1L)

        repeat(8) {
            val batch = store.nextBatch()
            if (batch.isNotEmpty()) {
                store.markInFlight(batch)
                store.reconcile(emptySet())     // back to PENDING
                store.markFailed(batch, "503", maxAttempts = 8)
            }
        }
        assertEquals(1, store.poisoned().size, "repeated failures must eventually poison")
    }
}
