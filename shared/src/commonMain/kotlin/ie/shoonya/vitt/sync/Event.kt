package ie.shoonya.vitt.sync

/**
 * One field of one entity changing, once. The unit of everything that syncs.
 *
 * Per-field rather than per-row, because two devices editing different fields of
 * the same transaction should both win. Row-level last-write-wins would discard
 * one device's entire edit to keep the other's.
 *
 * [hlc] is the primary key *and* the idempotency key. It is minted once, before
 * the first send attempt, and never regenerated on retry — that is precisely
 * what makes a retry safe.
 */
data class Event(
    val hlc: Hlc,
    val entity: String,
    val entityId: String,
    val field: String,
    val value: TaggedValue,
) {
    /** The row as it lands in the sheet's append-only Events tab. */
    fun toRow(): List<String> =
        listOf(hlc.encode(), hlc.nodeId, entity, entityId, field, value.encode())

    companion object {
        const val COLUMNS = 6

        fun fromRow(row: List<String>): Event {
            require(row.size >= COLUMNS) { "event row has ${row.size} columns, need $COLUMNS" }
            return Event(
                hlc = Hlc.decode(row[0]),
                entity = row[2],
                entityId = row[3],
                field = row[4],
                value = TaggedValue.decode(row[5]),
            )
        }

        /**
         * Parses a row without throwing.
         *
         * The Events tab lives in a spreadsheet a person can edit, and the
         * Sheets API trims trailing empty cells — so clearing one value, sorting
         * the tab, or pasting over a row all produce a short row. Parsing with
         * [fromRow] then throws before reaching the thousands of good rows
         * below it, and one stray keystroke stops sync permanently.
         *
         * Data that came out of a spreadsheet is untrusted input, not a
         * programming error.
         */
        fun parseRow(row: List<String>): Result<Event> = runCatching { fromRow(row) }
    }
}

/**
 * A value with its type tagged in the text itself.
 *
 * Everything in a spreadsheet cell is ultimately text, and letting Sheets guess
 * types is how "1.234" becomes a different number depending on the spreadsheet's
 * locale, and how a leading "+" or "=" becomes a formula. Tagging removes the
 * guessing: `N:-1250` is always the integer -1250.
 */
sealed interface TaggedValue {
    fun encode(): String

    data object Null : TaggedValue {
        override fun encode() = "0:"
    }

    data class Num(val value: Long) : TaggedValue {
        override fun encode() = "N:$value"
    }

    data class Str(val value: String) : TaggedValue {
        override fun encode() = "S:$value"
    }

    data class Bool(val value: Boolean) : TaggedValue {
        override fun encode() = "B:${if (value) 1 else 0}"
    }

    companion object {
        fun decode(encoded: String): TaggedValue = when {
            encoded == "0:" -> Null
            encoded.startsWith("N:") -> Num(
                encoded.removePrefix("N:").toLongOrNull()
                    ?: throw IllegalArgumentException("bad number: $encoded")
            )
            encoded.startsWith("S:") -> Str(encoded.removePrefix("S:"))
            encoded.startsWith("B:") -> Bool(encoded.removePrefix("B:") == "1")
            else -> throw IllegalArgumentException("untagged value: '$encoded'")
        }
    }
}
