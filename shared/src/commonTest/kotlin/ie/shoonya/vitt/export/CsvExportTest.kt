package ie.shoonya.vitt.export

import ie.shoonya.vitt.capture.CsvImport
import ie.shoonya.vitt.model.AccountKind
import ie.shoonya.vitt.model.LedgerRepository
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvExportTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    private fun sept(d: Int) = Civil.toDays(2026, 9, d)
    private fun eur(minor: Long) = Money(minor, Currency.EUR)
    private fun inr(minor: Long) = Money(minor, Currency.INR)

    private fun rows(csv: String) = csv.trim().split("\r\n")

    // --- field escaping ---

    @Test
    fun `a plain field is not quoted`() {
        assertEquals("Tesco", CsvExport.field("Tesco"))
    }

    @Test
    fun `null is an empty field and never the word null`() {
        assertEquals("", CsvExport.field(null))
    }

    @Test
    fun `a comma forces quoting`() {
        assertEquals("\"Ryan, Sons & Co\"", CsvExport.field("Ryan, Sons & Co"))
    }

    @Test
    fun `a quote inside a quoted field is doubled`() {
        assertEquals("\"say \"\"hi\"\", then\"", CsvExport.field("say \"hi\", then"))
    }

    @Test
    fun `a newline forces quoting rather than breaking the row`() {
        // A note typed across two lines would otherwise silently become two
        // rows, and every column after it would shift.
        assertEquals("\"line one\nline two\"", CsvExport.field("line one\nline two"))
    }

    // --- transactions ---

    @Test
    fun `the header names every column and the row count matches`() {
        val r = repo()
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO")
        r.record("b", inr(-90_000), day = sept(3), merchant = "SWIGGY")
        val out = rows(r.exportTransactionsCsv())
        assertEquals(CsvExport.TRANSACTION_COLUMNS.joinToString(","), out[0])
        assertEquals(3, out.size)
    }

    @Test
    fun `rows are oldest first — the order a statement is read in`() {
        val r = repo()
        r.record("late", eur(-1_000), day = sept(9), merchant = "LATE")
        r.record("early", eur(-1_000), day = sept(1), merchant = "EARLY")
        val out = rows(r.exportTransactionsCsv())
        assertTrue(out[1].startsWith("2026-09-01"))
        assertTrue(out[2].startsWith("2026-09-09"))
    }

    @Test
    fun `an amount is a plain signed decimal with no symbol and no grouping`() {
        val r = repo()
        r.record("a", eur(-1_234_567), day = sept(2), merchant = "BIG")
        val cells = rows(r.exportTransactionsCsv())[1].split(",")
        assertEquals("EUR", cells[1])
        // Not "-€12,345.67": a symbol and a thousands separator both have to be
        // stripped again by whatever reads this, and the separator is a comma.
        assertEquals("-12345.67", cells[2])
    }

    @Test
    fun `income keeps its sign so a salary cannot be read as rent`() {
        val r = repo()
        r.record("in", eur(340_000), day = sept(1), merchant = "PAYROLL")
        assertEquals("3400.00", rows(r.exportTransactionsCsv())[1].split(",")[2])
    }

    @Test
    fun `a zero-exponent currency is not scaled by a hundred`() {
        val r = repo()
        r.record("j", Money(-5_000, Currency.JPY), day = sept(2), merchant = "SHOP")
        val cells = rows(r.exportTransactionsCsv())[1].split(",")
        assertEquals("JPY", cells[1])
        assertEquals("-5000", cells[2])
    }

    @Test
    fun `the merchant is the raw acquirer string the bank sent`() {
        val r = repo()
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO STORES 3421 DUBLIN IE")
        // Not the cleaned "Tesco" the screens show. The raw string is the truth,
        // and an export that loses it is not lossless.
        assertTrue(rows(r.exportTransactionsCsv())[1].contains("TESCO STORES 3421 DUBLIN IE"))
    }

    @Test
    fun `an account is named rather than exported as an opaque id`() {
        val r = repo()
        r.openAccount("acc-7f3a", "Revolut EUR", Currency.EUR, AccountKind.CURRENT)
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO", accountId = "acc-7f3a")
        val line = rows(r.exportTransactionsCsv())[1]
        assertTrue(line.contains("Revolut EUR"))
        assertFalse(line.contains("acc-7f3a,"))
    }

    @Test
    fun `an archived account still names its own history`() {
        val r = repo()
        r.openAccount("old", "Old Card", Currency.EUR, AccountKind.CREDIT)
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO", accountId = "old")
        r.setAccountArchived("old", true)
        assertTrue(rows(r.exportTransactionsCsv())[1].contains("Old Card"))
    }

    @Test
    fun `a split exports the total fronted and who it was with`() {
        val r = repo()
        r.record(
            "s", eur(-2_000), day = sept(2), merchant = "DINNER",
            totalPaid = eur(-6_000), splitWith = setOf("bea@example.com", "ann@example.com"),
        )
        val cells = rows(r.exportTransactionsCsv())[1].split(",")
        val columns = CsvExport.TRANSACTION_COLUMNS
        assertEquals("-20.00", cells[columns.indexOf("amount")])
        assertEquals("-60.00", cells[columns.indexOf("total_paid")])
        // Semicolons inside the field, so a list does not force the whole row
        // into quotes on every export.
        assertEquals("ann@example.com;bea@example.com", cells[columns.indexOf("split_with")])
        assertEquals("ann@example.com=20.00;bea@example.com=20.00", cells[columns.indexOf("split_shares")])
    }

    @Test
    fun `an ordinary row carries no settled figure to interpret`() {
        val r = repo()
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO")
        val cells = rows(r.exportTransactionsCsv())[1].split(",")
        assertEquals("", cells[CsvExport.TRANSACTION_COLUMNS.indexOf("settled")])
    }

    @Test
    fun `a deleted transaction is not exported`() {
        val r = repo()
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO")
        r.record("b", eur(-2_000), day = sept(3), merchant = "LIDL")
        r.delete("a")
        val out = rows(r.exportTransactionsCsv())
        assertEquals(2, out.size)
        assertTrue(out[1].contains("LIDL"))
    }

    @Test
    fun `an empty ledger still exports a header`() {
        // A file with no header is not a CSV, and an empty export has to be
        // openable — otherwise the escape hatch fails exactly when someone is
        // checking whether it works.
        val out = rows(repo().exportTransactionsCsv())
        assertEquals(1, out.size)
        assertEquals(CsvExport.TRANSACTION_COLUMNS.joinToString(","), out[0])
    }

    @Test
    fun `there is no total row`() {
        val r = repo()
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO")
        r.record("b", inr(-90_000), day = sept(3), merchant = "SWIGGY")
        val out = rows(r.exportTransactionsCsv())
        // Two data rows and a header, and nothing that sums a euro to a rupee.
        assertEquals(3, out.size)
        assertFalse(out.any { it.contains("total", ignoreCase = true) && it != out[0] })
    }

    // --- transfers ---

    @Test
    fun `a same-currency transfer exports its fee and no rate`() {
        val r = repo()
        r.openAccount("cur", "Current", Currency.EUR, AccountKind.CURRENT, eur(100_000))
        r.openAccount("sav", "Savings", Currency.EUR, AccountKind.SAVINGS)
        r.transfer("t1", "cur", "sav", eur(50_000), eur(49_750), day = sept(4))
        val columns = CsvExport.TRANSFER_COLUMNS
        val cells = rows(r.exportTransfersCsv())[1].split(",")
        assertEquals("Current", cells[columns.indexOf("from_account")])
        assertEquals("Savings", cells[columns.indexOf("to_account")])
        assertEquals("2.50", cells[columns.indexOf("fee")])
        // Blank, not 1.0000 — a recorded 1 would be indistinguishable from an
        // observed rate.
        assertEquals("", cells[columns.indexOf("rate")])
    }

    @Test
    fun `a cross-currency transfer exports the observed rate and no invented fee`() {
        val r = repo()
        r.openAccount("eu", "Revolut EUR", Currency.EUR, AccountKind.CURRENT, eur(100_000))
        r.openAccount("in", "HDFC", Currency.INR, AccountKind.CURRENT)
        r.transfer("t2", "eu", "in", eur(30_000), inr(2_670_000), day = sept(5))
        val columns = CsvExport.TRANSFER_COLUMNS
        val cells = rows(r.exportTransfersCsv())[1].split(",")
        assertEquals("EUR", cells[columns.indexOf("sent_currency")])
        assertEquals("INR", cells[columns.indexOf("received_currency")])
        assertEquals("89.000000", cells[columns.indexOf("rate")])
        // The fee is inside the spread; separating it would need an outside
        // estimate, which §0.6 does not allow.
        assertEquals("", cells[columns.indexOf("fee")])
    }

    // --- round trip ---

    @Test
    fun `an exported file is readable by the app's own importer`() {
        // The escape hatch has to be a door, not a wall: if our own CSV reader
        // cannot parse our own CSV writer, no other tool's will either.
        val r = repo()
        r.record("a", eur(-1_000), day = sept(2), merchant = "TESCO STORES 3421")
        r.record("b", eur(-2_500), day = sept(3), merchant = "Ryan, Sons & Co")
        r.record("c", eur(340_000), day = sept(1), merchant = "PAYROLL")
        val parsed = CsvImport.parse(r.exportTransactionsCsv(), Currency.EUR)
        assertEquals(3, parsed.rows.size)
        assertTrue(parsed.rows.all { it.isUsable })
        // The quoted merchant survived its comma rather than splitting the row —
        // had it not, the row would have shifted and the amount column would
        // have held a piece of the name.
        assertTrue(parsed.rows.any { it.description == "Ryan, Sons & Co" })
        assertTrue(parsed.rows.any { it.amount == eur(-2_500) })
    }
}
