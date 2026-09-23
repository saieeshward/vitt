package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.model.LedgerRepository
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DerivedSummaryTest {

    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    private fun txn(
        id: String = "t1",
        amount: Money = eur(-1250),
        category: String? = "groceries",
        day: Int = Civil.toDays(2026, 4, 3),
        totalPaid: Money? = null,
        deleted: Boolean = false,
    ) = Transaction(
        id = id,
        amount = amount,
        merchant = "Tesco",
        category = category,
        categorySource = null,
        accountId = "a1",
        day = day,
        totalPaid = totalPaid,
        splitWith = if (totalPaid != null) setOf("bea") else emptySet(),
        settled = Money(0, amount.currency),
        note = null,
        deleted = deleted,
    )

    private fun cell(row: List<Cell>, column: String) =
        row[DerivedSummary.COLUMNS.indexOf(column)]

    @Test
    fun `one month one currency one category is one row`() {
        val rows = DerivedSummary.rows(
            listOf(txn("a"), txn("b", amount = eur(-750))),
            2026,
        )
        val only = rows.single()
        assertEquals(YearMonth(2026, 4), only.month)
        assertEquals(eur(2000), only.spent)
        assertEquals(2, only.entries)
    }

    @Test
    fun `spending and income are separate magnitudes rather than a net`() {
        // A net would make the month rent and salary both landed look quiet,
        // and a dashboard built on it would be confidently wrong.
        val rows = DerivedSummary.rows(
            listOf(txn("out", amount = eur(-145000)), txn("in", amount = eur(318000))),
            2026,
        )
        val only = rows.single()
        assertEquals(eur(145000), only.spent)
        assertEquals(eur(318000), only.received)
    }

    @Test
    fun `currencies never mix`() {
        val rows = DerivedSummary.rows(
            listOf(txn("e"), txn("i", amount = Money(-50000, Currency.INR))),
            2026,
        )
        assertEquals(2, rows.size)
        assertEquals(setOf(Currency.EUR, Currency.INR), rows.map { it.currency }.toSet())
    }

    @Test
    fun `the total agrees with what the app shows`() {
        // The property that matters. If these two could disagree the
        // spreadsheet would hold a second truth and the person reading it
        // would have no way to tell which one was wrong.
        var t = 1_000L
        val repo = LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
        val day = Civil.toDays(2026, 4, 3)
        repo.record("a", eur(-1250), day = day, category = "groceries")
        repo.record("b", eur(-750), day = day, category = "dining")
        repo.record("c", eur(318000), day = day, category = "income")

        val ledger = repo.ledgers(YearMonth(2026, 4)).single { it.currency == Currency.EUR }
        val rows = DerivedSummary.rows(repo.transactions(), 2026)
        val spent = rows.fold(eur(0)) { acc, r -> acc + r.spent }
        val received = rows.fold(eur(0)) { acc, r -> acc + r.received }

        assertEquals(ledger.spent, spent)
        assertEquals(ledger.received, received)
    }

    @Test
    fun `a transfer is neither spending nor income`() {
        // Same gate the ledger card uses, and the reason the two figures agree.
        val rows = DerivedSummary.rows(listOf(txn(category = "transfer")), 2026)
        assertTrue(rows.all { it.spent.minor == 0L && it.received.minor == 0L })
    }

    @Test
    fun `a split counts your share rather than what you fronted`() {
        val rows = DerivedSummary.rows(
            listOf(txn(amount = eur(-2000), totalPaid = eur(-6000))),
            2026,
        )
        assertEquals(eur(2000), rows.single().spent)
    }

    @Test
    fun `a deleted row is not counted`() {
        assertTrue(DerivedSummary.rows(listOf(txn(deleted = true)), 2026).isEmpty())
    }

    @Test
    fun `only the year asked for is written`() {
        val rows = DerivedSummary.rows(
            listOf(txn("a"), txn("b", day = Civil.toDays(2025, 4, 3))),
            2026,
        )
        assertEquals(YearMonth(2026, 4), rows.single().month)
    }

    @Test
    fun `years lists every year with something in it`() {
        val all = listOf(
            txn("a", day = Civil.toDays(2024, 1, 1)),
            txn("b", day = Civil.toDays(2026, 1, 1)),
            txn("c", day = Civil.toDays(2026, 6, 1)),
        )
        assertEquals(listOf(2024, 2026), DerivedSummary.years(all))
    }

    @Test
    fun `rows read month then currency then category`() {
        val rows = DerivedSummary.rows(
            listOf(
                txn("late", day = Civil.toDays(2026, 6, 1), category = "dining"),
                txn("early", day = Civil.toDays(2026, 1, 1), category = "housing"),
            ),
            2026,
        )
        assertEquals(listOf(YearMonth(2026, 1), YearMonth(2026, 6)), rows.map { it.month })
    }

    @Test
    fun `uncategorised is named and sorts last`() {
        // It is a real group with a real total and one somebody may click into,
        // but it is the pile to work through rather than a category, so sorting
        // it into the As would hide that.
        val table = DerivedSummary.table(
            listOf(txn("a", category = null), txn("b", category = "dining")),
            2026,
        )
        assertEquals(Cell.Text("Dining"), cell(table[1], "Category"))
        assertEquals(Cell.Text(DerivedSummary.UNCATEGORISED), cell(table[2], "Category"))
    }

    @Test
    fun `money is a number and the month is text`() {
        val row = DerivedSummary.table(listOf(txn()), 2026)[1]
        assertEquals(Cell.Number("12.50"), cell(row, "Spent"))
        assertEquals(Cell.Text("2026-04"), cell(row, "Month"))
    }

    @Test
    fun `the category column carries the label rather than the stored code`() {
        val row = DerivedSummary.table(listOf(txn(category = "family_gifts")), 2026)[1]
        assertEquals(Cell.Text("Family & Gifts"), cell(row, "Category"))
    }

    @Test
    fun `a year with nothing in it still writes its header`() {
        assertEquals(1, DerivedSummary.table(emptyList(), 2026).size)
    }
}
