package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.sync.EventPage
import ie.shoonya.vitt.sync.SheetTransport

/**
 * Connects [ie.shoonya.vitt.sync.Syncer] to the real Sheets API.
 *
 * Everything interesting about a sync is decided in `Syncer`, which is why that
 * class names nothing from this package. This file is only the translation, and
 * it is deliberately thin enough to read in one sitting: any logic that grows
 * here is logic that has escaped its tests.
 *
 * The header row is the reason `firstDataRow` is 2 and not 1. `createSpreadsheet`
 * freezes one row per tab, and an off-by-one between writing and reading is a
 * whole device's events read twice or never.
 */
class SheetsTransport(
    private val sheets: SheetsClient,
    /** How many rows to pull per request. */
    private val pageSize: Int = 5_000,
) : SheetTransport {

    override suspend fun createLedger(title: String): String {
        // Both tabs up front. §2 keeps the event log and the human-readable
        // mirror in one file, and a file created with only one of them is a file
        // the app cannot complete later: `drive.file` means it cannot go looking
        // for a spreadsheet it half-made.
        val created = sheets.createSpreadsheet(title, tabs = listOf(EVENTS_TAB, "Transactions"))
        // The header, written once, so a person opening the sheet sees columns
        // rather than six unlabelled strings. It is also what makes row 1 a
        // header rather than an event, which every read here assumes.
        sheets.append(
            created.spreadsheetId,
            EVENTS_TAB,
            listOf(listOf("hlc", "node", "entity", "id", "field", "value")),
        )
        return created.spreadsheetId
    }

    override suspend fun appendEvents(spreadsheetId: String, rows: List<List<String>>) {
        sheets.append(spreadsheetId, EVENTS_TAB, rows)
    }

    override suspend fun readEvents(spreadsheetId: String, fromRow: Int): EventPage {
        val page = sheets.readPaged(
            spreadsheetId = spreadsheetId,
            tab = EVENTS_TAB,
            columns = "AF",
            pageSize = pageSize,
            startRow = fromRow.coerceAtLeast(FIRST_DATA_ROW),
        )
        return EventPage(rows = page.rows, nextRow = page.nextRow)
    }

    override suspend fun ledgerVersion(spreadsheetId: String): String? =
        sheets.fileVersion(spreadsheetId).version

    /**
     * The derived-tab half of the same transport.
     *
     * Implemented here rather than as a second class because it is the same
     * translation against the same client, and separating it would mean two
     * objects holding one connection with no boundary between them worth
     * defending.
     */
    val derived: DerivedTabPort = object : DerivedTabPort {
        override suspend fun read(spreadsheetId: String, tab: String): List<List<String>> {
            // From row 1, not row 2: the header is the thing being read. Every
            // other read in this file starts at 2 because the header is known
            // in advance, and here it is precisely what is not.
            val page = sheets.readPaged(
                spreadsheetId = spreadsheetId,
                tab = tab,
                columns = "AZ",
                pageSize = pageSize,
                startRow = 1,
            )
            return page.rows
        }

        override suspend fun replace(
            spreadsheetId: String,
            tab: String,
            rows: List<List<Cell>>,
            lastColumn: Char,
        ) {
            sheets.replaceValues(spreadsheetId, tab, rows, lastColumn)
        }

        override suspend fun ensureTab(spreadsheetId: String, tab: String) {
            sheets.addTab(spreadsheetId, tab)
        }
    }

    companion object {
        const val EVENTS_TAB = "Events"

        /** Row 1 is the frozen header. */
        const val FIRST_DATA_ROW = 2
    }
}
