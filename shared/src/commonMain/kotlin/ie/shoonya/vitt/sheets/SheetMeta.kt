package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.sync.Iso8601

/**
 * The slice of the Sheets API the `_Meta` tab needs.
 *
 * Separate from [DerivedTabPort] because `_Meta` is the other tier: a data tab,
 * appended to and never rewritten, read as plain strings like the event log.
 */
interface MetaPort {
    /** The whole tab as strings, header row included. A missing tab is created and reads empty. */
    suspend fun read(spreadsheetId: String, tab: String): List<List<String>>

    /** Appends below whatever is there, verbatim. */
    suspend fun append(spreadsheetId: String, tab: String, rows: List<List<String>>)

    /** The spreadsheet's own locale setting, as Google reports it. */
    suspend fun locale(spreadsheetId: String): String?
}

/**
 * The `_Meta` tab: what the spreadsheet says about the apps that write to it.
 *
 * §2.2 names four things (schema version, app version, a device registry and a
 * locale stamp) and §2.8 builds the migration path on the first. Without it,
 * every device is guessing what shape the file is in from the shape of the file.
 *
 * ## Append-only, like the log
 *
 * A Tier-1 data tab, so nothing here is ever edited: each fact is a row, and a
 * later row of the same key supersedes an earlier one. That keeps §0.5 true (the
 * app never edits a row it did not just write) and makes two devices stamping
 * at once harmless, since both rows land and they say compatible things.
 *
 * ## What the schema version is for
 *
 * Not for deciding whether to migrate. §2.8 is explicit that each step checks
 * the actual state of the file, never the recorded number, which is what
 * [SheetDrift] does with the header. The number is for the one question state
 * cannot answer: *was this file shaped by a newer app than me?* If it was, this
 * device's idea of a correct header is out of date, and archiving the newer tab
 * as unusable would start a copy war with the phone that wrote it. So an older
 * app leaves the derived tabs alone until it is updated.
 */
object SheetMeta {

    const val TAB = "_Meta"

    /**
     * The shape of the file this build writes. Raise it in the same change that
     * alters a derived tab's columns, so an older build stops touching them.
     */
    const val SCHEMA_VERSION = 1

    val COLUMNS = listOf("key", "value", "device", "written_at")

    const val KEY_SCHEMA = "schema_version"
    const val KEY_APP = "app_version"
    const val KEY_LOCALE = "locale"

    /** What the tab says, read back. */
    data class State(
        /** The highest schema any app has stamped. Null on a file that predates `_Meta`. */
        val schemaVersion: Int?,
        /** The device registry: each device's most recent app version. */
        val devices: Map<String, String>,
        /** The spreadsheet locale last seen. */
        val locale: String?,
        /** A tab with rows in it and no header this code can find. */
        val unreadable: Boolean = false,
    ) {
        companion object {
            val EMPTY = State(schemaVersion = null, devices = emptyMap(), locale = null)
        }
    }

    /** What this device knows about itself, and when. */
    data class Us(
        val device: String,
        val appVersion: String,
        val schemaVersion: Int = SCHEMA_VERSION,
        val nowMillis: Long,
    )

    sealed interface Standing {
        /** The file is a shape this build understands. */
        data object Current : Standing

        /** A newer app has written to this file. The derived tabs are its to shape. */
        data class Newer(val sheetSchema: Int) : Standing
    }

    /**
     * Reads the tab. Columns by name, per §2.6, because it is still a tab in a
     * person's spreadsheet and still somewhere a column can be inserted.
     */
    fun read(rows: List<List<String>>): State {
        val header = rows.firstOrNull()?.map { it.trim() }
        if (header == null || header.all { it.isEmpty() }) {
            return if (rows.drop(1).any { r -> r.any { it.isNotBlank() } }) State.EMPTY.copy(unreadable = true)
            else State.EMPTY
        }
        val at = COLUMNS.associateWith { header.indexOf(it) }
        if (at.values.any { it < 0 }) return State.EMPTY.copy(unreadable = true)

        var schema: Int? = null
        val devices = linkedMapOf<String, String>()
        var locale: String? = null
        rows.drop(1).forEach { row ->
            fun cell(column: String) = row.getOrNull(at.getValue(column))?.trim().orEmpty()
            val value = cell("value")
            when (cell("key")) {
                // The highest, not the latest: a device still on an old build
                // stamping after a newer one must not lower the file's version.
                KEY_SCHEMA -> value.toIntOrNull()?.let { v -> schema = maxOf(schema ?: v, v) }
                KEY_APP -> cell("device").takeIf { it.isNotEmpty() && value.isNotEmpty() }
                    ?.let { devices[it] = value }
                KEY_LOCALE -> if (value.isNotEmpty()) locale = value
            }
        }
        return State(schema, devices, locale)
    }

    fun standing(state: State, us: Us): Standing {
        val theirs = state.schemaVersion ?: return Standing.Current
        return if (theirs > us.schemaVersion) Standing.Newer(theirs) else Standing.Current
    }

    /**
     * The rows to append so the tab tells the truth about this device, or
     * nothing when it already does. Every sync calls this, so the ordinary
     * answer is an empty list and no write at all.
     */
    fun due(state: State, us: Us, observedLocale: String?): List<List<String>> {
        if (state.unreadable) return emptyList()
        val at = Iso8601.format(us.nowMillis)
        fun row(key: String, value: String) = listOf(key, value, us.device, at)

        val rows = mutableListOf<List<String>>()
        // Only ever upward. A newer schema already recorded is left standing.
        if ((state.schemaVersion ?: 0) < us.schemaVersion) rows += row(KEY_SCHEMA, us.schemaVersion.toString())
        if (state.devices[us.device] != us.appVersion) rows += row(KEY_APP, us.appVersion)
        if (observedLocale != null && observedLocale != state.locale) rows += row(KEY_LOCALE, observedLocale)
        return rows
    }

    /**
     * One pass: read the tab, stamp what has changed, and say whether the
     * derived tabs are this build's to write.
     *
     * [checkLocale] is off for most syncs. A person changes the locale about
     * never, and syncs come every couple of seconds while somebody is typing,
     * against a quota of sixty requests a minute.
     */
    suspend fun sync(port: MetaPort, spreadsheetId: String, us: Us, checkLocale: Boolean = true): Standing {
        val existing = port.read(spreadsheetId, TAB)
        val state = read(existing)
        val standing = standing(state, us)

        val due = due(state, us, if (checkLocale) port.locale(spreadsheetId) else null)
        if (due.isNotEmpty()) {
            val header = if (existing.none { r -> r.any { it.isNotBlank() } }) listOf(COLUMNS) else emptyList()
            port.append(spreadsheetId, TAB, header + due)
        }
        return standing
    }
}
