package ie.shoonya.vitt.export

import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.model.Transfer
import ie.shoonya.vitt.time.Civil

/**
 * The escape hatch.
 *
 * `PLAN.md:144` puts it plainly: nobody scales *on* Sheets, so an export path
 * has to exist from v1 or the sheet becomes the trap the whole no-backend premise
 * claims it is not. It is also on §7's list of what launching with real depth
 * means, and Apple 4.2 is the most likely rejection for a lean tracker.
 *
 * Two rules govern what comes out:
 *
 * 1. **Faithful, not pretty.** Amounts are plain decimals with no symbol and no
 *    grouping; dates are ISO-8601; the merchant is the raw acquirer string the
 *    bank actually sent, not the cleaned label the app shows. An export the user
 *    cannot re-import losslessly is not an escape hatch, and every one of those
 *    prettifications is lossy.
 * 2. **One currency per row, never a total.** There is no summary row and no
 *    grand total, for the same reason no screen has one (§0.6). A spreadsheet
 *    can sum a column itself, and if the user chooses to sum two currencies
 *    together that is their arithmetic, not the app's claim.
 *
 * Transactions and transfers are separate files rather than one with a `kind`
 * column: a transfer has two amounts and two accounts, and forcing both shapes
 * into one row set would leave half the columns blank on every row.
 */
object CsvExport {

    /**
     * RFC 4180 line ending.
     *
     * CRLF because that is what the spec says and what Excel on Windows expects;
     * Sheets, Numbers and every library accept it. LF-only files are the ones
     * that open as a single row somewhere.
     */
    private const val EOL = "\r\n"

    /** Separator inside a single field that holds a list, e.g. split participants. */
    private const val LIST_SEPARATOR = ";"

    val TRANSACTION_COLUMNS = listOf(
        "date",
        "currency",
        "amount",
        "merchant",
        "category",
        "category_source",
        "account",
        "total_paid",
        "split_with",
        "split_shares",
        "settled",
        "note",
        "id",
    )

    val TRANSFER_COLUMNS = listOf(
        "date",
        "from_account",
        "to_account",
        "sent_currency",
        "sent",
        "received_currency",
        "received",
        "rate",
        "fee",
        "note",
        "id",
    )

    /**
     * @param accountName resolves an account id to its name. Ids are opaque and
     * mean nothing outside the app, so the name is what goes in the column —
     * this is the one place a raw stored value is deliberately not exported,
     * because the id is an implementation detail rather than the user's data.
     */
    fun transactions(
        transactions: List<Transaction>,
        accountName: (String) -> String? = { null },
    ): String = buildCsv(TRANSACTION_COLUMNS, transactions.sortedBy { it.day }) { t ->
        listOf(
            Civil.isoDate(t.day),
            t.amount.currency.code,
            // Signed, as stored: negative is money out. The sign is the only
            // record of direction, so dropping it would make an export
            // ambiguous between a salary and the rent.
            t.amount.toPlainString(),
            t.merchant,
            t.category,
            t.categorySource?.code,
            t.accountId?.let(accountName),
            // Set only on a split — the full amount fronted, of which `amount`
            // is the user's own share.
            t.totalPaid?.toPlainString(),
            t.splitWith.sorted().joinToString(LIST_SEPARATOR).ifEmpty { null },
            // Each person's part, in its own column rather than folded into
            // split_with, so software already reading that column keeps
            // reading a list of names. Equal parts are written out too: an
            // export is read without this app's rule for what "equal" means.
            t.shares().entries.joinToString(LIST_SEPARATOR) { (who, m) -> "$who=${m.toPlainString()}" }.ifEmpty { null },
            // Only meaningful for a split; a zero on every ordinary row would
            // read as a column the user has to interpret. Everything that has
            // come back, whether recorded per person or as one total.
            if (t.isSplit) t.totalSettled.toPlainString() else null,
            t.note,
            t.id,
        )
    }

    fun transfers(
        transfers: List<Transfer>,
        accountName: (String) -> String? = { null },
    ): String = buildCsv(TRANSFER_COLUMNS, transfers.sortedBy { it.day }) { t ->
        listOf(
            Civil.isoDate(t.day),
            accountName(t.fromAccountId) ?: t.fromAccountId,
            accountName(t.toAccountId) ?: t.toAccountId,
            t.sent.currency.code,
            t.sent.toPlainString(),
            t.received.currency.code,
            t.received.toPlainString(),
            // The observed rate, and blank for a same-currency move rather than
            // 1.0000 — a recorded 1 would be indistinguishable from a rate that
            // was actually observed (§0.6).
            t.rate?.toPlainString(),
            // Knowable only within one currency; across two it is inside the
            // spread and separating it would need an outside estimate.
            t.fee?.toPlainString(),
            t.note,
            t.id,
        )
    }

    private fun <T> buildCsv(
        columns: List<String>,
        rows: List<T>,
        cells: (T) -> List<String?>,
    ): String = buildString {
        append(columns.joinToString(",") { field(it) })
        append(EOL)
        rows.forEach { row ->
            append(cells(row).joinToString(",") { field(it) })
            append(EOL)
        }
    }

    /**
     * One field, quoted per RFC 4180 when it has to be.
     *
     * Null is an empty field, not the string "null". Quoting only when needed
     * keeps a hand-read file legible, and a quote inside a quoted field is
     * doubled — the one escaping rule the format has.
     *
     * Note what this deliberately does *not* do: it does not defang a merchant
     * called `=HYPERLINK(...)` by prefixing an apostrophe. That is the usual
     * advice, and it is wrong here — this export exists so the user's data can
     * leave losslessly, and silently editing their merchant names to protect a
     * spreadsheet they own defeats the only reason the file exists. The strings
     * come from the user's own bank and go back to the user.
     */
    internal fun field(value: String?): String {
        if (value == null) return ""
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}

/**
 * Every transaction, oldest first, with account names resolved.
 *
 * Archived accounts are included in the lookup: an account being archived does
 * not unname the transactions that went through it, and an export that says
 * "unknown account" for last year's card is a worse file than one that names it.
 */
fun ie.shoonya.vitt.model.LedgerRepository.exportTransactionsCsv(): String {
    val names = accounts(includeArchived = true).associate { it.id to it.name }
    return CsvExport.transactions(transactions()) { names[it] }
}

/** Every transfer, oldest first, with account names resolved. */
fun ie.shoonya.vitt.model.LedgerRepository.exportTransfersCsv(): String {
    val names = accounts(includeArchived = true).associate { it.id to it.name }
    return CsvExport.transfers(transfers()) { names[it] }
}
