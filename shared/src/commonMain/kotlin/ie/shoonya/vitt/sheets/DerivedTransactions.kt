package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.time.Civil

/**
 * One cell of a derived tab, carrying whether it is a number or text.
 *
 * The distinction is the whole reason this type exists. Everything VITT has
 * written to a spreadsheet so far is an event row, which is machine-readable by
 * design and never looked at; a derived tab is the opposite, and the first thing
 * anybody does with a column of money is select it and expect a sum. Sent as
 * text it sums to nothing.
 *
 * The other half is that text must stay inert. `valueInputOption=RAW` is what
 * keeps a merchant called `=cmd|...` a merchant rather than a formula, and keeps
 * `03/04/2026` from being reparsed by the spreadsheet's locale. RAW applies to
 * the *string* form, so the only way to get a real number under it is to send
 * JSON's number type — which is what [Number] does, and why it holds a decimal
 * string rather than a Double. Money is integer minor units everywhere in this
 * app and it is not going to become a float on the way out of it.
 */
sealed interface Cell {
    data class Text(val value: String) : Cell

    /** A plain decimal, written verbatim into the JSON as a number. */
    data class Number(val plain: String) : Cell

    /** An empty cell. Distinct from "0" and from the string "". */
    data object Blank : Cell
}

/**
 * The `Transactions` tab: what somebody actually reads when they open the
 * spreadsheet VITT made for them.
 *
 * §2.2 calls this the tab "the user actually reads and edits" and it has been
 * created empty since the sync path was built — the file has held nothing but a
 * hidden event log, which makes the product's own premise ("it is your
 * spreadsheet") true in letter and false in practice.
 *
 * This is deliberately not the CSV export. The export is everything, ids
 * included, for taking the data somewhere else; it is read by another program.
 * This is read by a person, so it names accounts rather than identifying them,
 * leads with the date and the description, and leaves a blank where the CSV
 * writes an empty field. The id column stays, last and narrow, because it is
 * the only way to point at a row when something looks wrong.
 *
 * ## Derived, and therefore disposable
 *
 * Every row here is recomputed from the local fold and written wholesale. That
 * is safe *only* because the app wrote it: §0.5 forbids editing a row the app
 * did not write, and forbids trusting anything the sheet computed. Nothing here
 * is ever read back. A person who edits this tab is editing a rendering, and
 * their edit is gone at the next write — which is why the write archives first
 * rather than assuming nobody has.
 */
object DerivedTransactions {

    /**
     * Written as row 1 and frozen. Title Case rather than the export's
     * `snake_case`: those headers are parsed by other software, and these are
     * read by a person.
     */
    val COLUMNS = listOf(
        "Date",
        "Description",
        "Category",
        "Account",
        "Currency",
        "Amount",
        "Your share",
        "Split with",
        "Note",
        "id",
    )

    /**
     * The whole tab, header included, oldest first.
     *
     * Oldest first because a spreadsheet grows downward and a person scrolling
     * to the bottom expects to find today there. The app's own Activity list is
     * newest first for the opposite reason: a screen is read from the top.
     */
    fun table(
        transactions: List<Transaction>,
        accountName: (String) -> String? = { null },
    ): List<List<Cell>> =
        listOf(COLUMNS.map { Cell.Text(it) }) + rows(transactions, accountName)

    fun rows(
        transactions: List<Transaction>,
        accountName: (String) -> String? = { null },
    ): List<List<Cell>> = transactions
        // Deleted rows are absent rather than struck through. The tab is a
        // rendering of what is true now, and a tombstone is a fact about the
        // log, which is one tab over.
        .filterNot { it.deleted }
        .sortedWith(compareBy({ it.day }, { it.id }))
        .map { t ->
            listOf(
                Cell.Text(Civil.isoDate(t.day)),
                // The cleaned merchant, as the app shows it. The raw string is
                // in the event log for anyone who wants it; a person reading
                // this wants "Tesco", not "TESCO STORES 3421 DUBLIN".
                text(t.merchantLabel ?: t.note),
                // The label, never the stored code: "Family & Gifts" rather
                // than "family_gifts".
                text(t.categoryOrNull?.label ?: t.category),
                text(t.accountId?.let(accountName)),
                Cell.Text(t.amount.currency.code),
                // Signed, as stored: negative is money out. The sign is the
                // only record of direction, and a column of magnitudes would
                // make a salary indistinguishable from the rent.
                //
                // On a split this is the *full* amount that left the account,
                // because that is what the bank statement will say. The share
                // the budget counts goes in its own column beside it.
                number(if (t.isSplit) t.totalPaid?.toPlainString() else t.amount.toPlainString()),
                // Blank on an ordinary row rather than a repeat of Amount: a
                // column that is either "the same again" or "different" is read
                // by scanning for the difference, and filling it destroys that.
                if (t.isSplit) number(t.amount.toPlainString()) else Cell.Blank,
                text(t.splitWith.sorted().joinToString(", ").ifEmpty { null }),
                // Only when it is not already the description, or the same
                // words appear twice on one line.
                text(t.note?.takeIf { t.merchantLabel != null }),
                Cell.Text(t.id),
            )
        }

    private fun text(value: String?): Cell =
        if (value.isNullOrBlank()) Cell.Blank else Cell.Text(value)

    private fun number(plain: String?): Cell =
        if (plain == null) Cell.Blank else Cell.Number(plain)
}
