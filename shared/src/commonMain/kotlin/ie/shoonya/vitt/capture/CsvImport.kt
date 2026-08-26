package ie.shoonya.vitt.capture

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money

data class CsvRow(
    val lineNumber: Int,
    val date: String?,
    val description: String?,
    val amount: Money?,
    val problems: List<String>,
) {
    val isUsable: Boolean get() = amount != null && problems.none { it.startsWith("fatal") }
}

data class CsvImportResult(
    val rows: List<CsvRow>,
    val detectedColumns: Map<String, Int>,
    val skippedLines: Int,
) {
    val usable: List<CsvRow> get() = rows.filter { it.isUsable }
    val needsReview: List<CsvRow> get() = rows.filterNot { it.isUsable }
}

/**
 * Reads a bank CSV export.
 *
 * Every bank invents its own layout, so nothing here assumes column positions.
 * Headers are matched by name against a synonym list, which is the same
 * discipline the sheet integration uses — users reorder and insert columns, and
 * position-based parsing breaks silently when they do.
 *
 * Two shapes of amount column exist in the wild and both must work: a single
 * signed column, or separate debit and credit columns. Getting this wrong
 * inverts every transaction in the file.
 */
object CsvImport {

    // Includes non-English headers: a euro-area user may well be handed a German
    // or French export, and rejecting the whole file over its header language is
    // a poor first impression.
    private val DATE_HEADERS = listOf(
        "date", "transaction date", "posted date", "completed date", "started date",
        "value date", "txn date", "booking date", "date posted",
        "datum", "buchungstag", "valuta", "fecha", "data",
    )
    private val DESCRIPTION_HEADERS = listOf(
        "description", "merchant", "details", "narrative", "reference",
        "transaction description", "name", "payee", "particulars", "remarks",
        "beschreibung", "verwendungszweck", "buchungstext", "concepto", "libelle",
    )
    private val AMOUNT_HEADERS = listOf(
        "amount", "value", "transaction amount", "amt", "betrag", "importe", "montant",
    )
    private val DEBIT_HEADERS = listOf(
        "debit", "withdrawal", "money out", "paid out", "dr", "withdrawal amt", "soll",
    )
    private val CREDIT_HEADERS = listOf(
        "credit", "deposit", "money in", "paid in", "cr", "deposit amt", "haben",
    )
    private val CURRENCY_HEADERS = listOf("currency", "ccy", "curr", "waehrung", "währung", "divisa")

    fun parse(content: String, defaultCurrency: Currency): CsvImportResult {
        val lines = content.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return CsvImportResult(emptyList(), emptyMap(), 0)

        val delimiter = detectDelimiter(lines)
        val headerIndex = findHeaderRow(lines, delimiter)
        if (headerIndex < 0) {
            return CsvImportResult(
                rows = listOf(CsvRow(1, null, null, null, listOf("fatal: no recognisable header row"))),
                detectedColumns = emptyMap(),
                skippedLines = lines.size,
            )
        }

        val header = splitLine(lines[headerIndex], delimiter).map { it.trim().lowercase() }

        // Debit and credit are resolved first and claim their columns. Without
        // this, a header like "Withdrawal Amt" matches the debit list on
        // "withdrawal" AND the amount list on "amt"; if the amount branch then
        // wins, every credit row is read from the debit column and silently
        // imports as zero — income disappears from the ledger.
        val debitIdx = header.indexOfFirstMatching(DEBIT_HEADERS)
        val creditIdx = header.indexOfFirstMatching(CREDIT_HEADERS)
        val claimed = setOfNotNull(
            debitIdx.takeIf { it >= 0 },
            creditIdx.takeIf { it >= 0 },
        )
        val amountIdx = header.indexOfFirstMatching(AMOUNT_HEADERS, exclude = claimed)

        val cols = mapOf(
            "date" to header.indexOfFirstMatching(DATE_HEADERS),
            "description" to header.indexOfFirstMatching(DESCRIPTION_HEADERS),
            "amount" to amountIdx,
            "debit" to debitIdx,
            "credit" to creditIdx,
            "currency" to header.indexOfFirstMatching(CURRENCY_HEADERS),
        ).filterValues { it >= 0 }

        // A real header resolves at least two *distinct* columns. One column
        // doing every job means the delimiter was wrong and the "header" is an
        // undivided line — from which every amount would be fabricated by
        // stripping non-digits out of the whole row.
        if (cols.values.distinct().size < 2) {
            return CsvImportResult(
                rows = listOf(
                    CsvRow(headerIndex + 1, null, null, null,
                        listOf("fatal: could not separate columns — is the delimiter right?")),
                ),
                detectedColumns = cols,
                skippedLines = lines.size,
            )
        }

        val rows = lines.drop(headerIndex + 1).mapIndexed { i, line ->
            parseRow(line, delimiter, cols, headerIndex + i + 2, defaultCurrency)
        }

        return CsvImportResult(rows, cols, headerIndex)
    }

    private fun List<String>.indexOfFirstMatching(
        candidates: List<String>,
        exclude: Set<Int> = emptySet(),
    ): Int {
        // Exact match wins over substring match, so "amount" beats "withdrawal amt".
        forEachIndexed { i, cell ->
            if (i !in exclude && candidates.any { it == cell }) return i
        }
        // Substring matching only for tokens long enough to be distinctive.
        // "cr" and "dr" are real header names but appear inside "description",
        // which would otherwise be detected as a credit column.
        forEachIndexed { i, cell ->
            if (i !in exclude && candidates.any { it.length >= 4 && cell.contains(it) }) return i
        }
        return -1
    }

    /**
     * Picks the delimiter by majority across the first several lines.
     *
     * Deciding from line one alone breaks on the preamble banks routinely emit
     * ("Kontoauszug 08/2026"), which contains no delimiter at all: every
     * candidate scores 1, and `maxByOrNull` returns the first on a tie — a
     * comma. A semicolon-delimited file then parses as one giant column, and
     * because that column contains both "datum" and "betrag" it is *accepted* as
     * the header, after which every amount is fabricated from a whole line.
     */
    internal fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(25)
        return listOf(',', ';', '\t', '|').maxByOrNull { candidate ->
            sample.sumOf { splitLine(it, candidate).size - 1 }
        } ?: ','
    }

    private fun findHeaderRow(lines: List<String>, delimiter: Char): Int {
        // Banks prepend account summaries and blank rows before the real header.
        lines.take(25).forEachIndexed { i, line ->
            val cells = splitLine(line, delimiter).map { it.trim().lowercase() }
            val hasDate = cells.any { c -> DATE_HEADERS.any { c.contains(it) } }
            val hasMoney = cells.any { c ->
                (AMOUNT_HEADERS + DEBIT_HEADERS + CREDIT_HEADERS).any { c.contains(it) }
            }
            if (hasDate && hasMoney) return i
        }
        return -1
    }

    /** RFC 4180 splitting: quoted fields may contain the delimiter, and "" is an escaped quote. */
    internal fun splitLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseRow(
        line: String,
        delimiter: Char,
        cols: Map<String, Int>,
        lineNumber: Int,
        defaultCurrency: Currency,
    ): CsvRow {
        val problems = mutableListOf<String>()
        val cells = splitLine(line, delimiter).map { it.trim().removeSurrounding("\"") }

        fun cell(name: String): String? =
            cols[name]?.let { cells.getOrNull(it) }?.takeIf { it.isNotBlank() }

        // C3: a Wise or Revolut export carries rows in currencies this app does
        // not model. Falling back to the default stamps CHF 340 as EUR 340 —
        // silently, at face value — which is precisely the blending the product
        // exists to refuse.
        val declared = cell("currency")
        val currency = if (declared != null) {
            Currency.ofCode(declared) ?: run {
                problems += "fatal: unsupported currency '$declared'"
                defaultCurrency
            }
        } else {
            defaultCurrency
        }
        val date = cell("date")
        if (date == null) problems += "no date"
        val description = cell("description")

        val hasPair = cols.containsKey("debit") || cols.containsKey("credit")
        val amount = when {
            cols.containsKey("amount") && !hasPair -> cell("amount")?.let { raw ->
                parseSignedAmount(raw, currency, problems)
            }
            // Separate debit/credit columns: exactly one should be populated, and
            // debit must come back negative.
            else -> {
                val debit = cell("debit")?.let { parseSignedAmount(it, currency, problems)?.abs() }
                val credit = cell("credit")?.let { parseSignedAmount(it, currency, problems)?.abs() }
                when {
                    debit != null && credit != null -> {
                        problems += "both debit and credit populated"
                        null
                    }
                    debit != null -> -debit
                    credit != null -> credit
                    else -> null
                }
            }
        }
        if (amount == null && problems.isEmpty()) problems += "no amount"

        return CsvRow(lineNumber, date, description, amount, problems)
    }

    private fun parseSignedAmount(raw: String, currency: Currency, problems: MutableList<String>): Money? {
        var s = raw.trim()
        var negative = false

        // Accounting negatives: (12.50) means -12.50.
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }

        // C4: German and Austrian exports write debits with a *trailing* minus
        // (1.234,56-), and many Indian and UK exports use a DR/CR suffix. The
        // digit filter below strips both, so without this every debit imported
        // as income — with the header lists advertising German support.
        val upper = s.uppercase()
        when {
            upper.endsWith("DR") -> { negative = true; s = s.dropLast(2).trim() }
            upper.endsWith("CR") -> { negative = false; s = s.dropLast(2).trim() }
        }
        if (s.endsWith("-")) { negative = true; s = s.dropLast(1).trim() }

        if (s.startsWith("-")) { negative = true; s = s.substring(1) }
        if (s.startsWith("+")) s = s.substring(1)
        s = s.filter { it.isDigit() || it == '.' || it == ',' }
        if (s.isEmpty()) return null

        val warnings = mutableListOf<String>()
        val match = AmountParser.interpretNumber(s, currency, warnings)
        problems += warnings
        if (match == null) { problems += "unparseable amount '$raw'"; return null }
        return Money(if (negative) -match.abs else match.abs, currency)
    }
}
