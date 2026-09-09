package ie.shoonya.vitt.sync

import ie.shoonya.vitt.sheets.SheetsError

/**
 * One page of the Events tab, and where the next read should start.
 *
 * Its own type rather than the Sheets client's, so this file names nothing from
 * the transport and a test can drive it with an in-memory sheet.
 */
data class EventPage(val rows: List<List<String>>, val nextRow: Int)

/**
 * The slice of the Sheets API a sync actually needs.
 *
 * Narrow on purpose. The whole `SheetsClient` carries spreadsheet creation,
 * paging, Drive metadata and the RAW/USER_ENTERED distinction; a syncer that
 * took the concrete class would be untestable without HTTP, and the tests that
 * matter here are precisely the ones about a request whose response never
 * arrives.
 */
interface SheetTransport {
    /** Creates the ledger spreadsheet and returns its id. */
    suspend fun createLedger(title: String): String

    /** Appends rows to the Events tab. Atomic per call, and never an upsert. */
    suspend fun appendEvents(spreadsheetId: String, rows: List<List<String>>)

    /** Reads the Events tab from [fromRow] onward. */
    suspend fun readEvents(spreadsheetId: String, fromRow: Int): EventPage
}

/** What one sync cycle did. */
data class SyncOutcome(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val acked: Int = 0,
    val requeued: Int = 0,
    val poisoned: Int = 0,
    /** Rows in the sheet that could not be parsed. Reported, never discarded. */
    val unreadable: List<EventLog.UnreadableRow> = emptyList(),
    /** Null when the cycle completed. Set when it stopped early. */
    val error: SheetsError? = null,
) {
    val ok: Boolean get() = error == null
}

/**
 * Moves events between this device's log and the user's spreadsheet.
 *
 * All the hard parts already exist elsewhere and this is the orchestration:
 * [EventStore] owns the durable outbox and the reconcile, [EventLog] parses
 * rows, [Bisect] finds the one bad row in a rejected batch, and the HLC is the
 * idempotency key that makes every retry safe. What was missing was anything
 * that called them in order.
 *
 * The order is deliberate and each step depends on the one before:
 *
 * 1. **Recover.** Anything left IN_FLIGHT by a previous run is resolved against
 *    what the sheet actually holds. This comes first because [EventStore.nextBatch]
 *    returns nothing while a batch is in flight — so without recovery, one
 *    crash mid-send would wedge the queue permanently.
 * 2. **Push**, oldest first, one batch at a time.
 * 3. **Pull**, from the last row this device read.
 *
 * Push before pull so that a device which has been offline contributes its work
 * before it takes on anyone else's, which keeps the common case — one device,
 * nothing to pull — to a single write.
 *
 * `PLAN.md` §0.5 governs the whole file: the sheet is an append-only log and
 * never a computation the app trusts, the local database is the sole source of
 * truth, and nothing here ever edits a row it did not just write.
 */
class Syncer(
    private val store: EventStore,
    private val transport: SheetTransport,
    private val now: () -> Long,
    /** Rows per append. Sheets counts a batch as one write against the quota. */
    private val batchSize: Long = 500,
    private val title: String = "VITT ledger",
) {

    /**
     * The spreadsheet id, creating the file on first use.
     *
     * Persisted the moment it exists. Losing this id is unrecoverable under
     * `drive.file`: the scope grants access per file, and the app is not allowed
     * to search Drive to find one it forgot — so the id must reach storage
     * before anything else can go wrong.
     */
    suspend fun ensureLedger(): String {
        store.get(EventStore.KEY_SPREADSHEET_ID)?.let { return it }
        val id = transport.createLedger(title)
        store.put(EventStore.KEY_SPREADSHEET_ID, id)
        return id
    }

    /** One full cycle. Never throws for a [SheetsError]; it comes back in the outcome. */
    suspend fun sync(): SyncOutcome {
        val id = try {
            ensureLedger()
        } catch (e: SheetsError) {
            return SyncOutcome(error = e)
        }

        val recovered = try {
            recover(id)
        } catch (e: SheetsError) {
            return SyncOutcome(error = e)
        }

        val pushed = push(id)
        if (pushed.error != null) {
            return pushed.copy(acked = recovered.acked, requeued = recovered.requeued)
        }

        val pulled = try {
            pull(id)
        } catch (e: SheetsError) {
            return pushed.copy(
                acked = recovered.acked,
                requeued = recovered.requeued,
                error = e,
            )
        }

        // Acked entries have served their purpose once they are in the sheet.
        store.garbageCollect()

        return SyncOutcome(
            pushed = pushed.pushed,
            pulled = pulled.first,
            acked = recovered.acked,
            requeued = recovered.requeued,
            poisoned = pushed.poisoned,
            unreadable = pulled.second,
        )
    }

    /**
     * Resolves whatever a previous run left in flight.
     *
     * The expensive read is only paid when there is something to resolve, which
     * is the rare case: a send that was interrupted between the request leaving
     * and the response arriving.
     */
    private suspend fun recover(spreadsheetId: String): ReconcileResult {
        if (store.outboxCounts()[OutboxState.IN_FLIGHT].let { it == null || it == 0L }) {
            return ReconcileResult(acked = 0, requeued = 0)
        }
        // Read from the top: an in-flight entry may have landed at any row, and
        // guessing a tail window would ack the wrong thing.
        val page = transport.readEvents(spreadsheetId, fromRow = 2)
        val confirmed = page.rows.mapNotNull { it.firstOrNull() }.toSet()
        return store.reconcile(confirmed)
    }

    private suspend fun push(spreadsheetId: String): SyncOutcome {
        var sent = 0
        var poisoned = 0
        while (true) {
            val batch = store.nextBatch(batchSize)
            if (batch.isEmpty()) return SyncOutcome(pushed = sent, poisoned = poisoned)

            store.markInFlight(batch)
            try {
                transport.appendEvents(spreadsheetId, batch.map { it.toRow() })
                store.markAcked(batch)
                sent += batch.size
            } catch (e: SheetsError) {
                if (e is SheetsError.BadRequest) {
                    // A 400 is the one error that says something definite: the
                    // request was understood and rejected, so nothing landed.
                    // Find which rows are actually to blame rather than
                    // condemning 500 financial records for one of them.
                    poisoned += bisect(spreadsheetId, batch, e)
                    continue
                }
                // Everything else leaves the batch IN_FLIGHT, which is what that
                // state is for: "handed to the transport; outcome unknown".
                //
                // This is the trap the first version of this file walked into. It
                // marked a transport failure FAILED and let the next cycle send
                // again — but a dropped connection cannot tell you whether the
                // server committed first, and Sheets has no idempotency key, so
                // a blind resend appends a second copy of a real expense.
                // Leaving it in flight forces the next cycle through `recover`,
                // which asks the sheet what it actually holds. That is the only
                // honest answer available.
                //
                // Attempts are deliberately not incremented here, so a week
                // offline cannot poison a transaction the user believes is
                // saved. Only a rejection can poison anything.
                return SyncOutcome(pushed = sent, poisoned = poisoned, error = e)
            }
        }
    }

    /** @return how many events were poisoned. */
    private suspend fun bisect(
        spreadsheetId: String,
        batch: List<Event>,
        error: SheetsError.BadRequest,
    ): Int {
        val offenders = Bisect.failing(batch) { slice ->
            try {
                transport.appendEvents(spreadsheetId, slice.map { it.toRow() })
                true
            } catch (_: SheetsError.BadRequest) {
                false
            }
        }
        val rest = batch.filterNot { row -> offenders.any { it.hlc == row.hlc } }
        store.poisonOnly(offenders, emptyList(), error.message ?: "rejected")
        // The non-offenders are already in the sheet, so they are acked, not
        // requeued. Every row not returned as an offender was part of a send
        // that returned true, and a send that returned true committed — the
        // recursion only ends on a slice that succeeded or a single row that
        // fails alone. Requeuing them, which is what `poisonOnly` does with its
        // second argument, would append each of them a second time.
        store.markAcked(rest)
        return offenders.size
    }

    private suspend fun pull(spreadsheetId: String): Pair<Int, List<EventLog.UnreadableRow>> {
        val from = store.lastReadRow().coerceAtLeast(2)
        val page = transport.readEvents(spreadsheetId, fromRow = from)
        if (page.rows.isEmpty()) return 0 to emptyList()

        val read = EventLog.readRows(page.rows, firstRowNumber = from)
        val added = store.appendRemote(read.events)
        store.rememberReadRow(page.nextRow)
        return added to read.unreadable
    }
}
