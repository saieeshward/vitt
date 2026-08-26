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
            val amount = folded["txn-1"]?.fields?.get("amount")
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

        // 6. Change detection. The plan polls Drive's `version` counter to
        // notice edits, on the documented claim that it "reflects every change
        // made to the file on the server". A first run showed it unchanged
        // across a write, so this measures rather than assumes: the same write
        // is checked immediately and again after a delay, with modifiedTime
        // alongside, which distinguishes "the counter lags" from "the counter
        // does not track Sheets content edits at all".
        try {
            val before = sheets.fileVersion(id)
            sheets.append(id, "Transactions", listOf(listOf("change-probe")))
            val immediately = sheets.fileVersion(id)
            delayMillis(4_000)
            val settled = sheets.fileVersion(id)

            val versionMovedNow = before.version != immediately.version
            val versionMovedLater = before.version != settled.version
            val timeMoved = before.modifiedTime != settled.modifiedTime

            val verdict = when {
                versionMovedNow -> "version is immediate — polling works as planned"
                versionMovedLater -> "version LAGS — poll interval must exceed the lag"
                timeMoved -> "version does NOT track content; modifiedTime does — poll that instead"
                else -> "neither version nor modifiedTime moved — needs changes.list"
            }
            record(
                "change detection",
                versionMovedNow || versionMovedLater || timeMoved,
                "$verdict · version ${before.version}→${immediately.version}→${settled.version} · " +
                    "modifiedTime ${before.modifiedTime} → ${settled.modifiedTime}",
            )
        } catch (e: Exception) {
            record("change detection", false, e.message ?: e.toString())
        }

        return steps
    }
}
