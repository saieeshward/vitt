package ie.shoonya.vitt.sync

import ie.shoonya.vitt.sheets.SheetsError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sync cycle, against a sheet that behaves like the real one.
 *
 * The interesting cases are all failures, and one above all: the append reached
 * the server and the response never came back. Sheets has no upsert, no
 * conditional write and no idempotency key, so a blind retry there appends a
 * second copy of a real expense — and a duplicated expense is a wrong total the
 * user will not notice for weeks.
 */
class SyncerTest {

    private val node = "a219e7a71cc18912"

    /**
     * A sheet with the guarantees the API gives and none of the ones it does not.
     *
     * Row 1 is the header, so the first data row is 2 and reads are offset the
     * way `readPaged` offsets them — an off-by-one here is a whole device's
     * events read twice or never.
     */
    private class Sheet : SheetTransport {
        val rows = mutableListOf<List<String>>()
        var appends = 0
        var created = 0

        /** Set to make the next append fail *before* committing. Cleared after it fires. */
        var failBeforeWrite: (() -> Nothing)? = null

        /**
         * Commit the next append, then throw as if the response was lost.
         *
         * The one case the whole design exists to survive, so it is a mode on
         * the fake rather than something a test assembles by hand.
         */
        var loseNextResponse = false

        /** Rows matching this are rejected as malformed, however often retried. */
        var rejectIf: ((List<String>) -> Boolean)? = null

        override suspend fun createLedger(title: String): String {
            created++
            return "sheet-$created"
        }

        override suspend fun appendEvents(spreadsheetId: String, rows: List<List<String>>) {
            appends++
            failBeforeWrite?.let { boom -> failBeforeWrite = null; boom() }
            rejectIf?.let { bad ->
                if (rows.any(bad)) throw SheetsError.BadRequest(400, "row rejected")
            }
            this.rows += rows
            if (loseNextResponse) {
                loseNextResponse = false
                throw SheetsError.Transport("connection dropped after the server committed")
            }
        }

        override suspend fun readEvents(spreadsheetId: String, fromRow: Int): EventPage {
            // fromRow is a spreadsheet row number and row 1 is the header.
            val start = (fromRow - 2).coerceAtLeast(0)
            val slice = rows.drop(start)
            return EventPage(slice, nextRow = 2 + rows.size)
        }

        fun hlcs(): List<String> = rows.map { it[0] }
        fun duplicates(): List<String> =
            hlcs().groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toList()
    }

    private fun store(id: String = node): EventStore {
        var t = 1_000L
        return EventStore.open(testDriver(), id) { t++ }
    }

    private fun EventStore.write(vararg ids: String) {
        ids.forEach { i ->
            append(
                Event(issue(), "transaction", i, "amount", TaggedValue.Num(-1250)),
                nowMillis = 1_700_000_000_000,
            )
        }
    }

    @Test
    fun `the first sync creates the ledger and every later one reuses it`() = runTest {
        val sheet = Sheet()
        val store = store()
        val syncer = Syncer(store, sheet, now = { 1L })

        store.write("t1")
        assertTrue(syncer.sync().ok)
        assertEquals(1, sheet.created)

        store.write("t2")
        assertTrue(syncer.sync().ok)
        // Creating a second file would orphan the first, and under `drive.file`
        // the app cannot search Drive to find the one it abandoned.
        assertEquals(1, sheet.created)
        assertEquals("sheet-1", store.get(EventStore.KEY_SPREADSHEET_ID))
    }

    @Test
    fun `pushing drains the outbox`() = runTest {
        val sheet = Sheet()
        val store = store()
        store.write("t1", "t2", "t3")

        val outcome = Syncer(store, sheet, now = { 1L }).sync()

        assertTrue(outcome.ok, outcome.error?.message)
        assertEquals(3, outcome.pushed)
        assertEquals(3, sheet.rows.size)
        assertEquals(0L, store.pendingCount())
    }

    @Test
    fun `a response lost after the server committed does not duplicate the row`() = runTest {
        val sheet = Sheet()
        val store = store()
        store.write("t1")

        // The rows land, then the connection dies before the ack. The entry is
        // left IN_FLIGHT, which is the state the whole design exists to survive.
        sheet.loseNextResponse = true
        val first = Syncer(store, sheet, now = { 1L }).sync()
        assertFalse(first.ok)

        // The next cycle reconciles against the sheet, finds the HLC already
        // there, and acks rather than sending again.
        val second = Syncer(store, sheet, now = { 1L }).sync()
        assertTrue(second.ok, second.error?.message)
        assertEquals(1, second.acked)
        assertEquals(0, second.requeued)
        assertEquals(emptyList(), sheet.duplicates())
        assertEquals(1, sheet.rows.size)
    }

    @Test
    fun `a failure before the write sends the event again`() = runTest {
        val sheet = Sheet()
        val store = store()
        store.write("t1")

        sheet.failBeforeWrite = { throw SheetsError.Transport("connection refused") }
        assertFalse(Syncer(store, sheet, now = { 1L }).sync().ok)
        assertEquals(0, sheet.rows.size)

        // Nothing was lost: the event is still queued and goes on the retry.
        val second = Syncer(store, sheet, now = { 1L }).sync()
        assertTrue(second.ok, second.error?.message)
        assertEquals(1, sheet.rows.size)
    }

    @Test
    fun `one malformed row is poisoned and the rest of the batch still lands`() = runTest {
        val sheet = Sheet()
        val store = store()
        store.write("good1", "bad", "good2")
        // A row is [hlc, nodeId, entity, entityId, field, value], so the
        // entity id is index 3 — index 2 is "transaction" for every row.
        sheet.rejectIf = { row -> row.getOrNull(3) == "bad" }

        val outcome = Syncer(store, sheet, now = { 1L }).sync()

        // A 400 fails the whole append, so without the bisect all three would be
        // condemned for one of them.
        assertEquals(1, outcome.poisoned)
        assertEquals(1, store.poisoned().size)
        val landed = sheet.rows.map { it[3] }.toSet()
        assertTrue("good1" in landed, "good rows: $landed")
        assertTrue("good2" in landed, "good rows: $landed")
        assertFalse("bad" in landed)
        // And each landed exactly once. The bisect's successful sub-sends commit,
        // so requeuing the survivors afterwards would append every good row a
        // second time — which an earlier version of `bisect` did.
        assertEquals(emptyList(), sheet.duplicates())
        assertEquals(2, sheet.rows.size)
        assertEquals(0L, store.pendingCount())
    }

    @Test
    fun `pulling ingests remote events once and then does nothing`() = runTest {
        val sheet = Sheet()
        // Another device's work, already in the sheet.
        val other = store("b319e7a71cc18913")
        other.write("remote1", "remote2")
        sheet.rows += other.nextBatch(500).map { it.toRow() }

        val mine = store()
        val syncer = Syncer(mine, sheet, now = { 1L })
        mine.put(EventStore.KEY_SPREADSHEET_ID, "sheet-1")

        val first = syncer.sync()
        assertTrue(first.ok, first.error?.message)
        assertEquals(2, first.pulled)

        // A second cycle must not re-read what it already has: the read row is
        // remembered, and re-reading the whole tab every sync is what §2 calls
        // out as a hundred round trips at fifty thousand transactions.
        val second = syncer.sync()
        assertEquals(0, second.pulled)
    }

    @Test
    fun `two devices exchanging through the sheet reach the same state`() = runTest {
        val sheet = Sheet()
        val a = store("a219e7a71cc18912")
        val b = store("b319e7a71cc18913")
        a.put(EventStore.KEY_SPREADSHEET_ID, "sheet-1")
        b.put(EventStore.KEY_SPREADSHEET_ID, "sheet-1")

        a.write("from-a")
        b.write("from-b")

        // Each pushes, then each pulls, twice, so both have seen everything.
        repeat(2) {
            Syncer(a, sheet, now = { 1L }).sync()
            Syncer(b, sheet, now = { 1L }).sync()
        }

        // Convergence is the engine's whole claim: the log is the truth and the
        // state is derived, so two devices holding the same log must agree.
        assertEquals(a.fold().keys, b.fold().keys)
        assertEquals(
            a.fold().mapValues { it.value.fields },
            b.fold().mapValues { it.value.fields },
        )
    }

    @Test
    fun `an expired token stops the cycle and leaves the queue intact`() = runTest {
        val sheet = Sheet()
        val store = store()
        store.write("t1", "t2")
        sheet.failBeforeWrite = { throw SheetsError.Unauthorized("token expired") }

        val outcome = Syncer(store, sheet, now = { 1L }).sync()

        assertTrue(outcome.error is SheetsError.Unauthorized)
        assertEquals(0, sheet.rows.size)
        // Re-auth has to find the work still there. Dropping it would lose two
        // transactions the user believes are saved.
        assertEquals(2L, store.pendingCount())
    }

    @Test
    fun `a sheet row that cannot be parsed is reported and never silently dropped`() = runTest {
        val sheet = Sheet()
        sheet.rows += listOf("not-an-hlc", "junk")
        val store = store()
        store.put(EventStore.KEY_SPREADSHEET_ID, "sheet-1")

        val outcome = Syncer(store, sheet, now = { 1L }).sync()

        assertTrue(outcome.ok, outcome.error?.message)
        assertEquals(1, outcome.unreadable.size)
        // Row 2, because row 1 of a spreadsheet is the header.
        assertEquals(2, outcome.unreadable.first().rowNumber)
        assertNull(outcome.error)
    }
}
