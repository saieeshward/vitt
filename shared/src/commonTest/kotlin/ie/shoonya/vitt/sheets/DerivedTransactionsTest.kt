package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DerivedTransactionsTest {

    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    private fun txn(
        id: String = "t1",
        amount: Money = eur(-1250),
        merchant: String? = "TESCO STORES 3421 DUBLIN",
        category: String? = "groceries",
        accountId: String? = "a1",
        day: Int = Civil.toDays(2026, 4, 3),
        totalPaid: Money? = null,
        splitWith: Set<String> = emptySet(),
        note: String? = null,
        deleted: Boolean = false,
    ) = Transaction(
        id = id,
        amount = amount,
        merchant = merchant,
        category = category,
        categorySource = null,
        accountId = accountId,
        day = day,
        totalPaid = totalPaid,
        splitWith = splitWith,
        settled = Money(0, amount.currency),
        note = note,
        deleted = deleted,
    )

    private val names = { id: String -> if (id == "a1") "AIB" else null }

    private fun row(t: Transaction) = DerivedTransactions.rows(listOf(t), names).single()

    private fun column(name: String, t: Transaction) =
        row(t)[DerivedTransactions.COLUMNS.indexOf(name)]

    @Test
    fun `the header is row one and matches the columns`() {
        val table = DerivedTransactions.table(listOf(txn()), names)
        assertEquals(DerivedTransactions.COLUMNS.map { Cell.Text(it) }, table.first())
        assertEquals(DerivedTransactions.COLUMNS.size, table[1].size)
    }

    @Test
    fun `money is a number and everything else is text`() {
        // The whole reason Cell exists. A column of money sent as text sums to
        // nothing, which is the first thing anybody does with it.
        assertEquals(Cell.Number("-12.50"), column("Amount", txn()))
        assertTrue(column("Description", txn()) is Cell.Text)
        assertTrue(column("Date", txn()) is Cell.Text)
    }

    @Test
    fun `the date is ISO rather than anything a locale can reinterpret`() {
        assertEquals(Cell.Text("2026-04-03"), column("Date", txn()))
    }

    @Test
    fun `the sign carries the direction`() {
        // A column of magnitudes would make a salary indistinguishable from
        // the rent.
        assertEquals(Cell.Number("-12.50"), column("Amount", txn(amount = eur(-1250))))
        assertEquals(Cell.Number("318000.00"), column("Amount", txn(amount = eur(31800000))))
    }

    @Test
    fun `the merchant is cleaned and the category is its label`() {
        // A person reads this tab. "TESCO STORES 3421 DUBLIN" and
        // "family_gifts" are both stored values, and neither is for reading.
        assertEquals(Cell.Text("Tesco"), column("Description", txn()))
        assertEquals(Cell.Text("Groceries"), column("Category", txn()))
    }

    @Test
    fun `the account is named rather than identified`() {
        // An id means nothing outside the app, and this tab is outside the app.
        assertEquals(Cell.Text("AIB"), column("Account", txn()))
    }

    @Test
    fun `an unknown account leaves a blank rather than printing its id`() {
        assertEquals(Cell.Blank, column("Account", txn(accountId = "gone")))
        assertEquals(Cell.Blank, column("Account", txn(accountId = null)))
    }

    @Test
    fun `a row with no merchant falls back to the note`() {
        assertEquals(
            Cell.Text("Birthday dinner"),
            column("Description", txn(merchant = null, note = "Birthday dinner")),
        )
        // And is not then repeated in the Note column.
        assertEquals(Cell.Blank, column("Note", txn(merchant = null, note = "Birthday dinner")))
    }

    @Test
    fun `a split shows what left the account and what it cost you`() {
        // Amount is what the bank statement will say, because somebody
        // reconciling the two needs them to agree. The share the budget counts
        // sits beside it rather than replacing it.
        val t = txn(amount = eur(-2000), totalPaid = eur(-6000), splitWith = setOf("bea", "ali"))
        assertEquals(Cell.Number("-60.00"), column("Amount", t))
        assertEquals(Cell.Number("-20.00"), column("Your share", t))
        assertEquals(Cell.Text("ali, bea"), column("Split with", t))
    }

    @Test
    fun `an ordinary row leaves the share blank rather than repeating the amount`() {
        // A column that is either "the same again" or "different" is read by
        // scanning for the difference. Filling it destroys that.
        assertEquals(Cell.Blank, column("Your share", txn()))
    }

    @Test
    fun `a deleted row is absent rather than struck through`() {
        // The tab renders what is true now. A tombstone is a fact about the
        // log, which is the other tab.
        assertTrue(DerivedTransactions.rows(listOf(txn(deleted = true)), names).isEmpty())
    }

    @Test
    fun `rows run oldest first`() {
        // A spreadsheet grows downward, so today belongs at the bottom. The
        // app's Activity list is newest first for the opposite reason.
        val old = txn(id = "a", day = Civil.toDays(2026, 1, 1))
        val new = txn(id = "b", day = Civil.toDays(2026, 6, 1))
        val dates = DerivedTransactions.rows(listOf(new, old), names)
            .map { it[DerivedTransactions.COLUMNS.indexOf("Date")] }
        assertEquals(listOf(Cell.Text("2026-01-01"), Cell.Text("2026-06-01")), dates)
    }

    @Test
    fun `two rows on one day keep a stable order`() {
        // Otherwise the whole tab churns between writes and a diff is useless.
        val a = txn(id = "aaa")
        val b = txn(id = "bbb")
        val ids = { rows: List<List<Cell>> ->
            rows.map { it[DerivedTransactions.COLUMNS.indexOf("id")] }
        }
        assertEquals(
            ids(DerivedTransactions.rows(listOf(a, b), names)),
            ids(DerivedTransactions.rows(listOf(b, a), names)),
        )
    }

    @Test
    fun `a currency without decimals is not given any`() {
        val yen = txn(amount = Money(-1200, Currency.JPY))
        assertEquals(Cell.Number("-1200"), column("Amount", yen))
        assertEquals(Cell.Text("JPY"), column("Currency", yen))
    }

    @Test
    fun `an empty ledger still writes its header`() {
        // A tab with a header and no rows reads as "nothing yet". A tab with
        // nothing at all reads as broken.
        assertEquals(1, DerivedTransactions.table(emptyList(), names).size)
    }
}
