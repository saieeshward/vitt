package ie.shoonya.vitt.sync

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two silent lost-update bugs: a clock that forgets across launches, and a
 * clock that never learns from other devices.
 *
 * Both discard the user's own correction *after the UI has shown it accepted*,
 * which is the worst shape a bug of this kind can take.
 */
class ClockPersistenceTest {

    private val nodeA = "a219e7a71cc18912"
    private val nodeB = "b0f1c2d3e4f50617"

    private fun amount(hlc: Hlc, id: String, minor: Long) =
        Event(hlc, "transaction", id, "amount", TaggedValue.Num(minor))

    // ---- C7: the clock must survive a restart ------------------------------

    @Test
    fun `a restarted clock does not reissue timestamps it already used`() {
        val driver: SqlDriver = testDriver()

        // Session one: the phone's clock is four minutes fast.
        var wall = 4 * 60_000L
        val first = EventStore.open(driver, nodeA) { wall }
        val expensive = amount(first.issue(), "txn-1", -5000)
        first.append(expensive, wall)

        // NTP corrects the clock backwards, then the app restarts.
        wall = 0L
        val second = EventStore.open(driver, nodeA) { wall }
        val correction = amount(second.issue(), "txn-1", -500)
        second.append(correction, wall)

        assertTrue(
            correction.hlc > expensive.hlc,
            "the correction was made later and must win; got ${correction.hlc} vs ${expensive.hlc}",
        )
        assertEquals(
            TaggedValue.Num(-500),
            second.fold().getValue(EventLog.EntityKey("transaction", "txn-1")).fields["amount"],
            "the user's correction must survive a restart across a clock rollback",
        )
    }

    @Test
    fun `the seed survives losing sync_state — via the log itself`() {
        val driver: SqlDriver = testDriver()
        var wall = 10_000L
        val store = EventStore.open(driver, nodeA) { wall }
        val e = amount(store.issue(), "txn-1", -100)
        store.append(e, wall)

        // sync_state is wiped but the event log remains.
        store.put(EventStore.KEY_LAST_HLC, "")
        wall = 0L
        val reopened = EventStore.open(driver, nodeA) { wall }
        assertTrue(reopened.issue() > e.hlc, "the log is the fallback high-water mark")
    }

    // ---- C6: remote events must advance the local clock --------------------

    @Test
    fun `a local edit made after seeing a remote one wins`() {
        val driver: SqlDriver = testDriver()
        // This device's clock reads T. The other device's is 90s ahead — well
        // inside the drift bound, so nothing is "wrong" anywhere.
        var wall = 1_000L
        val store = EventStore.open(driver, nodeA) { wall }

        val remoteClock = HlcClock(nodeB, now = { 91_000L })
        val fromB = amount(remoteClock.issue(), "txn-1", -2000)
        store.appendRemote(listOf(fromB))

        // The user now corrects it on this device, ten seconds later by local time.
        wall = 11_000L
        val correction = amount(store.issue(), "txn-1", -1500)
        store.append(correction, wall)

        assertTrue(
            correction.hlc > fromB.hlc,
            "an edit made after seeing a change must order after it",
        )
        assertEquals(TaggedValue.Num(-1500), store.fold().getValue(EventLog.EntityKey("transaction", "txn-1")).fields["amount"])
    }

    @Test
    fun `observing is persisted — so it survives a restart too`() {
        val driver: SqlDriver = testDriver()
        val store = EventStore.open(driver, nodeA) { 1_000L }
        val fromB = amount(HlcClock(nodeB, now = { 200_000L }).issue(), "txn-1", -2000)
        store.appendRemote(listOf(fromB))

        val reopened = EventStore.open(driver, nodeA) { 2_000L }
        assertTrue(reopened.issue() > fromB.hlc)
    }

    @Test
    fun `one device with a broken clock cannot stall the others`() {
        // A remote event far in the future is stored — the fold orders by its
        // own timestamp — but must not drag this clock along with it, and must
        // not abort ingestion of the batch.
        val driver: SqlDriver = testDriver()
        val store = EventStore.open(driver, nodeA) { 1_000L }
        val absurd = amount(Hlc(999_999_999_999L, 0, nodeB), "txn-bad", -1)
        val sane = amount(HlcClock(nodeB, now = { 2_000L }).issue(), "txn-ok", -100)

        val stored = store.appendRemote(listOf(absurd, sane))
        assertEquals(2, stored, "both events are stored")
        assertTrue(
            store.issue().physicalMillis < 999_999_999_999L,
            "a bad remote clock must not poison this device's clock",
        )
    }

    // ---- local clock must never refuse to work -----------------------------

    @Test
    fun `a device whose own clock jumped forward can still record`() {
        // Previously this threw: the clock defended against its own owner and
        // the device could not log anything until real time caught up.
        val clock = HlcClock(nodeA, seed = Hlc(60 * 60_000L, 0, nodeA), now = { 0L })
        val issued = clock.issue()
        assertTrue(issued.physicalMillis >= 60 * 60_000L)
    }

    @Test
    fun `a saturated counter borrows a millisecond instead of failing`() {
        // 65,536 events inside one millisecond is reachable on a bulk import;
        // throwing would lose the import.
        val clock = HlcClock(nodeA, seed = Hlc(5_000L, Hlc.MAX_COUNTER, nodeA), now = { 5_000L })
        val next = clock.issue()
        assertEquals(5_001L, next.physicalMillis)
        assertEquals(0, next.counter)
    }
}
