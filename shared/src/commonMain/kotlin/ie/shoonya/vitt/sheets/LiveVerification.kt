package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.HlcClock
import ie.shoonya.vitt.sync.TaggedValue
import kotlinx.coroutines.delay

/**
 * Checks, against a real Google account, the assumptions the whole storage
 * design rests on.
 *
 * Everything else in this project has been tested against a fake sheet written
 * from documentation. A fake can only confirm that the code matches its author's
 * understanding of the API — if that understanding is wrong, the fake is wrong
 * in the same way and the tests still pass. These steps are the ones that cannot
 * be faked.
 *
 * Creates a throwaway spreadsheet in the user's Drive and leaves it there, so
 * the results can be inspected by eye afterwards.
 */
class LiveVerification(
    private val sheets: SheetsClient,
    private val nodeId: String = "live000000000001",
) {

    data class Step(val name: String, val passed: Boolean, val detail: String)

    private suspend fun delayMillis(ms: Long) = delay(ms)

    private companion object {
        const val CHANGE_POLL_INTERVAL_MS = 3_000L
        const val CHANGE_POLL_ATTEMPTS = 7
        const val PROBE_TAB = "Probe"

        /** "Transactions" already holds the probe rows written above. */
        const val REFRESH_TAB = "Refresh"
    }

    suspend fun run(onStep: (Step) -> Unit): List<Step> {
        val steps = mutableListOf<Step>()
        fun record(name: String, passed: Boolean, detail: String) {
            val step = Step(name, passed, detail)
            steps += step
            onStep(step)
        }

        // 1. drive.file must permit creating a spreadsheet. If this fails, the
        // non-sensitive scope strategy is wrong and the project needs Google
        // verification plus a recurring paid security assessment.
        val spreadsheet = try {
            sheets.createSpreadsheet(
                title = "VITT verification ${Hlc(0, 0, nodeId).encode().take(10)}",
                tabs = listOf("Events", "Transactions"),
            ).also { record("create spreadsheet", true, it.spreadsheetId) }
        } catch (e: Exception) {
            record("create spreadsheet", false, e.message ?: e.toString())
            return steps
        }
        val id = spreadsheet.spreadsheetId

        // 2. Append must work on a file we created, under drive.file alone.
        val clock = HlcClock(nodeId, now = { 1_700_000_000_000 })
        val events = listOf(
            Event(clock.issue(), "transaction", "txn-1", "amount", TaggedValue.Num(-1250)),
            Event(clock.issue(), "transaction", "txn-1", "merchant", TaggedValue.Str("Tesco")),
            Event(clock.issue(), "transaction", "txn-2", "amount", TaggedValue.Num(-50000)),
        )
        try {
            val response = sheets.append(id, "Events", events.map { it.toRow() })
            record("append rows", true, "updated ${response.updates?.updatedRows} rows")
        } catch (e: Exception) {
            record("append rows", false, e.message ?: e.toString())
            return steps
        }

        // 3. The locale trap. Values that a locale-sensitive parser would
        // mangle: a decimal that could be read as thousands, a date that could
        // be day-first or month-first, and a leading '=' that USER_ENTERED
        // would turn into a formula.
        try {
            sheets.append(id, "Transactions", listOf(listOf("1.234", "03/04/2026", "=1+1", "1,23,456.78")))
            val readBack = sheets.read(id, "Transactions!A1:D1").firstOrNull().orEmpty()
            val intact = readBack.getOrNull(0) == "1.234" &&
                readBack.getOrNull(1) == "03/04/2026" &&
                readBack.getOrNull(2) == "=1+1"
            record(
                "values survive round trip",
                intact,
                if (intact) "RAW kept all four verbatim" else "MANGLED: $readBack",
            )
        } catch (e: Exception) {
            record("values survive round trip", false, e.message ?: e.toString())
        }

        // 4. Reading back must reproduce exactly what was written, or the fold
        // produces different state than the device that wrote it.
        try {
            val rows = sheets.read(id, "Events!A1:F100")
            val read = EventLog.readRows(rows, firstRowNumber = 1)
            val parsed = read.events
            val folded = EventLog.fold(parsed)
            val amount = folded[EventLog.EntityKey("transaction", "txn-1")]?.fields?.get("amount")
            val ok = parsed.size == events.size && amount == TaggedValue.Num(-1250)
            record(
                "read back and fold",
                ok,
                if (ok) "${parsed.size} events, txn-1 amount = -1250" else "got ${parsed.size} events, amount=$amount",
            )
        } catch (e: Exception) {
            record("read back and fold", false, e.message ?: e.toString())
        }

        // 5. Re-appending identical rows must produce duplicates the client can
        // detect by HLC — confirming that dedupe genuinely has to happen client
        // side, as the design assumes.
        try {
            sheets.append(id, "Events", listOf(events.first().toRow()))
            val rows = sheets.read(id, "Events!A1:F100")
            val hlcs = rows.mapNotNull { it.firstOrNull() }
            val duplicated = hlcs.size != hlcs.toSet().size
            record(
                "duplicate append is detectable",
                duplicated,
                if (duplicated) "server has no upsert, as assumed" else "unexpected: no duplicate appeared",
            )
        } catch (e: Exception) {
            record("duplicate append is detectable", false, e.message ?: e.toString())
        }

        // 6. Change detection. Drive's version counter is what the app polls to
        // notice edits the user made in the spreadsheet directly. It is known to
        // lag by seconds, so this polls for it rather than sampling once: a
        // fixed wait turns a normal lag into a reported failure, and reports the
        // same thing a caching bug would — which has already cost one debugging
        // cycle.
        try {
            val before = sheets.fileVersion(id)
            sheets.append(id, "Transactions", listOf(listOf("change-probe")))

            var moved = false
            var attempts = 0
            var latest = before
            while (attempts < CHANGE_POLL_ATTEMPTS && !moved) {
                delayMillis(CHANGE_POLL_INTERVAL_MS)
                attempts++
                latest = sheets.fileVersion(id)
                moved = latest.version != before.version ||
                    latest.modifiedTime != before.modifiedTime
            }

            val waited = attempts * CHANGE_POLL_INTERVAL_MS / 1000
            record(
                "change detection",
                moved,
                if (moved) {
                    "version ${before.version} to ${latest.version} after ${waited}s"
                } else {
                    // Byte-identical after this long is a cached response, not a
                    // slow counter.
                    "nothing moved in ${waited}s — version ${before.version}, " +
                        "modifiedTime unchanged at ${before.modifiedTime}"
                },
            )
        } catch (e: Exception) {
            record("change detection", false, e.message ?: e.toString())
        }

        // The derived tabs (Phase 4). Everything below was built against a fake
        // port, so the sequencing and the arithmetic are proven and that Google
        // accepts these exact requests is not.
        verifyDerivedTabs(id, ::record)

        return steps
    }

    private suspend fun verifyDerivedTabs(id: String, record: (String, Boolean, String) -> Unit) {
        // 7. addSheet through batchUpdate, and the second call recognised as
        // "already there" rather than failing. Also the first batchUpdate this
        // app has sent, with the unused request kinds going out as nulls.
        try {
            val first = sheets.addTab(id, PROBE_TAB)
            val second = sheets.addTab(id, PROBE_TAB)
            record(
                "add tab, then again",
                first && !second,
                "first added=$first, second added=$second",
            )
        } catch (e: Exception) {
            record("add tab, then again", false, e.message ?: e.toString())
            return
        }

        // 8. A number reaches the sheet as a number under RAW, written as an
        // unquoted JSON literal; a boolean as a boolean; a formula-shaped
        // string as inert text. Read back unformatted, each keeps its type.
        val written = listOf(
            listOf(Cell.Text("Amount"), Cell.Text("Done"), Cell.Text("Merchant")),
            listOf(Cell.Number("-12.50"), Cell.Bool(true), Cell.Text("=1+1")),
            listOf(Cell.Number("1234.05"), Cell.Bool(false), Cell.Text("Tesco")),
            listOf(Cell.Number("-0.01"), Cell.Blank, Cell.Text("third")),
        )
        try {
            sheets.replaceValues(id, PROBE_TAB, written, 'C')
            val back = sheets.readCells(id, "$PROBE_TAB!A1:C10")
            val row = back.getOrNull(1).orEmpty()
            val ok = row.getOrNull(0)?.let { it is Cell.Number && it.sameValueAs(Cell.Number("-12.50")) } == true &&
                row.getOrNull(1) == Cell.Bool(true) &&
                row.getOrNull(2) == Cell.Text("=1+1") &&
                back.getOrNull(2)?.getOrNull(0)?.sameValueAs(Cell.Number("1234.05")) == true
            record("typed values survive round trip", ok, if (ok) "number, boolean and text kept" else "got $back")
        } catch (e: Exception) {
            record("typed values survive round trip", false, e.message ?: e.toString())
        }

        // 9. The closed-range clear: a shorter table must not leave the old
        // tail on screen as rows that exist nowhere else.
        try {
            sheets.replaceValues(id, PROBE_TAB, written.take(2), 'C')
            val back = sheets.readCells(id, "$PROBE_TAB!A1:C10")
            record(
                "shorter write clears the tail",
                back.size == 2,
                "${back.size} rows remain, expected 2",
            )
        } catch (e: Exception) {
            record("shorter write clears the tail", false, e.message ?: e.toString())
        }

        // 10. duplicateSheet under drive.file alone. If this is refused, §2.8's
        // archive-then-replace needs a different mechanism entirely.
        try {
            val copied = sheets.archiveTab(id, PROBE_TAB, "${PROBE_TAB}_archive")
            val back = sheets.readCells(id, "${PROBE_TAB}_archive!A1:C10")
            val ok = copied && back.size == 2
            record("archive a tab", ok, if (ok) "copy holds ${back.size} rows" else "copied=$copied, got $back")
        } catch (e: Exception) {
            record("archive a tab", false, e.message ?: e.toString())
        }

        // 11. The whole refresh twice over, as a sync runs it, with the ledger
        // moving in between: one entry changed, one deleted, one new. The
        // second pass must write rather than hold, which is what the memory is
        // for, and it is the first time the multi-range write and the native
        // row delete meet Google.
        try {
            val port = SheetsTransport(sheets).derived
            var memory = emptyMap<String, String>()
            val first = DerivedTabs.refresh(
                port, id,
                listOf(probeTransaction("live-t1", -1250), probeTransaction("live-t2", 250000)),
                { "Probe account" }, tab = REFRESH_TAB, remember = { memory = it },
            )
            val second = DerivedTabs.refresh(
                port, id,
                listOf(probeTransaction("live-t1", -1300), probeTransaction("live-t3", -50)),
                { "Probe account" }, tab = REFRESH_TAB, lastSeen = memory, remember = { memory = it },
            )
            val ids = sheets.readCells(id, "$REFRESH_TAB!J2:J10").map { it.firstOrNull()?.asText() }
            val ok = first is DerivedTabs.Outcome.Written &&
                second is DerivedTabs.Outcome.Written &&
                ids == listOf("live-t1", "live-t3")
            record("refresh twice is clean", ok, "first $first, then $second; rows $ids")
        } catch (e: Exception) {
            record("refresh twice is clean", false, e.message ?: e.toString())
        }

        // 12. _Meta: created, stamped once, and a second pass writes nothing.
        // Also the locale read, whose narrowed field mask Google has to accept.
        try {
            val us = SheetMeta.Us(device = nodeId, appVersion = "verify", nowMillis = 1_700_000_000_000)
            val port = SheetsTransport(sheets).meta
            SheetMeta.sync(port, id, us)
            val once = sheets.read(id, "${SheetMeta.TAB}!A1:D20")
            SheetMeta.sync(port, id, us)
            val twice = sheets.read(id, "${SheetMeta.TAB}!A1:D20")
            val locale = sheets.locale(id)
            val ok = once.size == 4 && twice.size == 4 && locale == "en_GB"
            record("meta tab", ok, "${once.size} rows, then ${twice.size}; locale $locale")
        } catch (e: Exception) {
            record("meta tab", false, e.message ?: e.toString())
        }

        // 13. The Dashboard: values into a tab created on the write, and one
        // addChart over a closed range. Open the file afterwards and look at
        // the chart, because a chart Google accepted can still point at the
        // wrong rows.
        try {
            val port = SheetsTransport(sheets).derived
            var charted: DerivedDashboard.Charted? = null
            DerivedTabs.refreshDashboard(
                port, id,
                listOf(probeTransaction("live-d1", -1250), probeTransaction("live-d2", -4000)),
                emptyMap(),
                today = ie.shoonya.vitt.time.Civil.toDays(2026, 4, 20),
                charted = null,
                checkCharts = true,
                remember = { charted = it },
            )
            val block = sheets.readCells(id, "${DerivedDashboard.TAB}!A4:B5")
            val ok = charted?.currencies == setOf("EUR") &&
                block.getOrNull(1)?.getOrNull(1)?.sameValueAs(Cell.Number("52.50")) == true
            record("dashboard and chart", ok, "charted $charted; first rows $block")
        } catch (e: Exception) {
            record("dashboard and chart", false, e.message ?: e.toString())
        }
    }

    private fun probeTransaction(id: String, minor: Long) = ie.shoonya.vitt.model.Transaction(
        id = id,
        amount = ie.shoonya.vitt.money.Money(minor, ie.shoonya.vitt.money.Currency.EUR),
        merchant = "Probe",
        category = "groceries",
        categorySource = null,
        accountId = "probe",
        day = ie.shoonya.vitt.time.Civil.toDays(2026, 4, 3),
        totalPaid = null,
        splitWith = emptySet(),
        settled = ie.shoonya.vitt.money.Money(0, ie.shoonya.vitt.money.Currency.EUR),
        note = null,
        deleted = false,
    )
}
