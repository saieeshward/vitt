package ie.shoonya.vitt.sync

import ie.shoonya.vitt.sheets.SheetsError
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * When a sync runs, not what it does. The cost model behind every assertion:
 * one append is one quota write however many rows it carries, and one read is
 * one of sixty a minute.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncControllerTest {

    private class Sheet : SheetTransport {
        val rows = mutableListOf<List<String>>()
        var appends = 0
        var fail: SheetsError? = null
        /** Something that is not a SheetsError at all: a bug in the transport. */
        var explode = false
        override suspend fun createLedger(title: String) = "sheet-1"
        override suspend fun appendEvents(spreadsheetId: String, rows: List<List<String>>) {
            if (explode) throw IllegalStateException("transport bug")
            fail?.let { throw it }
            appends++
            this.rows += rows
        }
        override suspend fun readEvents(spreadsheetId: String, fromRow: Int) =
            EventPage(rows.drop((fromRow - 2).coerceAtLeast(0)), 2 + rows.size)
        override suspend fun ledgerVersion(spreadsheetId: String) = "v$appends"
    }

    private fun store(): EventStore {
        var t = 1_000L
        return EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }
    }

    private fun EventStore.write(id: String) {
        append(Event(issue(), "transaction", id, "amount", TaggedValue.Num(-100)), nowMillis = 1L)
    }

    @Test
    fun `edits inside the debounce window reach the sheet as one append`() = runTest {
        val sheet = Sheet()
        val store = store()
        val controller = SyncController(
            scope = backgroundScope,
            isConnected = { true },
            syncer = { Syncer(store, sheet, now = { 1L }) },
            store = store,
            now = { 1L },
            debounceMillis = 2_000,
        )
        store.onLocalWrite = { controller.onLocalWrite() }

        store.write("t1"); advanceTimeBy(500)
        store.write("t2"); advanceTimeBy(500)
        store.write("t3")
        assertEquals(0, sheet.appends)

        advanceTimeBy(2_001); runCurrent()

        assertEquals(1, sheet.appends)
        assertEquals(3, sheet.rows.size)
        assertIs<SyncStatus.Idle>(controller.status.value)
        assertEquals(0L, (controller.status.value as SyncStatus.Idle).pending)
    }

    @Test
    fun `nothing runs while disconnected and the queue waits`() = runTest {
        val sheet = Sheet()
        val store = store()
        var connected = false
        val controller = SyncController(
            scope = backgroundScope,
            isConnected = { connected },
            syncer = { Syncer(store, sheet, now = { 1L }) },
            store = store,
            now = { 1L },
        )
        store.onLocalWrite = { controller.onLocalWrite() }

        store.write("t1")
        advanceTimeBy(10_000); runCurrent()
        controller.onForeground(); runCurrent()
        assertEquals(0, sheet.appends)
        assertEquals(SyncStatus.Off, controller.status.value)
        assertEquals(1L, store.pendingCount())

        // Connecting later drains what was recorded before.
        connected = true
        controller.onForeground(); runCurrent()
        assertEquals(1, sheet.appends)
        assertEquals(0L, store.pendingCount())
    }

    @Test
    fun `a failed cycle reports the error and keeps the queue`() = runTest {
        val sheet = Sheet().apply { fail = SheetsError.Transport("offline") }
        val store = store()
        val controller = SyncController(
            scope = backgroundScope,
            isConnected = { true },
            syncer = { Syncer(store, sheet, now = { 1L }) },
            store = store,
            now = { 1L },
        )
        store.write("t1")

        val outcome = controller.syncNow()

        assertTrue(outcome?.error is SheetsError.Transport)
        val status = assertIs<SyncStatus.Failed>(controller.status.value)
        assertEquals(1L, status.pending)
        // Recovery, not resend: the entry is in flight until the sheet is asked.
        assertEquals(1L, store.outboxCounts()[OutboxState.IN_FLIGHT])

        sheet.fail = null
        controller.syncNow()
        assertIs<SyncStatus.Idle>(controller.status.value)
        assertEquals(0L, store.pendingCount())
    }

    @Test
    fun `a pull from another device bumps the remote counter`() = runTest {
        val sheet = Sheet()
        val store = store()
        store.put(EventStore.KEY_SPREADSHEET_ID, "sheet-1")
        val controller = SyncController(
            scope = backgroundScope,
            isConnected = { true },
            syncer = { Syncer(store, sheet, now = { 1L }) },
            store = store,
            now = { 1L },
        )
        // A row another device wrote, through its own syncer.
        val other = EventStore.open(testDriver(), "b31f0d5c9e7a2210") { 5_000L }
        other.put(EventStore.KEY_SPREADSHEET_ID, "sheet-1")
        other.write("x")
        Syncer(other, sheet, now = { 1L }).sync()

        controller.syncNow()
        assertEquals(1, controller.remoteChanges.value)

        controller.syncNow()
        assertEquals(1, controller.remoteChanges.value)
    }

    @Test
    fun `a syncer that throws still leaves the status on Failed — never stuck on Syncing`() = runTest {
        val sheet = Sheet()
        val store = store()
        val controller = SyncController(
            scope = backgroundScope,
            isConnected = { true },
            syncer = { Syncer(store, sheet, now = { 1L }) },
            store = store,
            now = { 1L },
            debounceMillis = 0,
        )
        sheet.explode = true
        store.write("t1")
        controller.syncNow()
        runCurrent()
        val failed = assertIs<SyncStatus.Failed>(controller.status.value)
        assertEquals(1L, failed.pending)
    }

    private fun kotlinx.coroutines.test.TestScope.throttled(
        sheet: Sheet,
        store: EventStore,
        onRefresh: () -> Unit,
    ) = SyncController(
        scope = backgroundScope,
        isConnected = { true },
        syncer = { Syncer(store, sheet, now = { 1L }) },
        store = store,
        now = { 1L },
        debounceMillis = 2_000,
        refreshDerived = { onRefresh() },
        derivedCooldownMillis = 30_000,
    ).also { controller -> store.onLocalWrite = { controller.onLocalWrite() } }

    @Test
    fun `a burst of entries redraws the tabs twice — not once per entry`() = runTest {
        // Each redraw is about eight requests against sixty a minute. Five
        // entries a few seconds apart were five redraws and most of the quota.
        val sheet = Sheet()
        val store = store()
        var refreshes = 0
        throttled(sheet, store) { refreshes++ }

        repeat(5) { i ->
            store.write("t$i")
            advanceTimeBy(3_000); runCurrent()
        }
        assertEquals(5, sheet.appends, "every entry still reaches the sheet on its own cycle")
        assertEquals(1, refreshes, "the first redraw runs at once and the rest wait")

        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, refreshes, "what changed during the window is drawn once at its end")
    }

    @Test
    fun `a cycle that moved nothing does not redraw`() = runTest {
        val sheet = Sheet()
        val store = store()
        var refreshes = 0
        val controller = throttled(sheet, store) { refreshes++ }

        controller.onForeground(); runCurrent()
        assertEquals(1, refreshes, "the first redraw after launch runs, since the sheet may have been edited")

        advanceTimeBy(60_000); runCurrent()
        controller.onForeground(); runCurrent()
        assertEquals(1, refreshes)
    }

    @Test
    fun `sync now redraws through the cooldown`() = runTest {
        // Somebody pressed the button and is looking at the spreadsheet.
        val sheet = Sheet()
        val store = store()
        var refreshes = 0
        val controller = throttled(sheet, store) { refreshes++ }

        store.write("t1"); advanceTimeBy(2_001); runCurrent()
        store.write("t2")
        controller.syncNow(); runCurrent()
        assertEquals(2, refreshes)
    }

    @Test
    fun `a failed redraw is tried again after the window rather than at once`() = runTest {
        val sheet = Sheet()
        val store = store()
        var attempts = 0
        val controller = throttled(sheet, store) {
            attempts++
            if (attempts == 1) throw IllegalStateException("tab write failed")
        }

        controller.onForeground(); runCurrent()
        assertIs<SyncStatus.Idle>(controller.status.value, "a tab failing is not a sync failing")
        assertEquals(1, attempts)

        advanceTimeBy(30_001); runCurrent()
        assertEquals(2, attempts)
    }
}
