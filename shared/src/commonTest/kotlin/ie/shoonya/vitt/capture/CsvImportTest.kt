package ie.shoonya.vitt.capture

import ie.shoonya.vitt.money.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CsvImportTest {

    @Test
    fun `revolut shaped export`() {
        val csv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            CARD_PAYMENT,Current,2026-08-20 09:12:03,2026-08-20 11:00:00,Tesco Stores,-12.50,0.00,EUR,COMPLETED,987.50
            TOPUP,Current,2026-08-21 08:00:00,2026-08-21 08:00:01,Salary,2500.00,0.00,EUR,COMPLETED,3487.50
        """.trimIndent()

        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(2, r.usable.size)
        assertEquals(-1250L, r.usable[0].amount?.minor)
        assertEquals(250000L, r.usable[1].amount?.minor)
        assertEquals(Currency.EUR, r.usable[0].amount?.currency)
    }

    @Test
    fun `separate debit and credit columns`() {
        // AIB/BOI shaped. Debit must come back negative even though the cell is unsigned.
        val csv = """
            Posted Date,Description,Debit,Credit,Balance
            20/08/2026,TESCO STORES DUBLIN,12.50,,987.50
            21/08/2026,SALARY,,2500.00,3487.50
        """.trimIndent()

        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(2, r.usable.size)
        assertEquals(-1250L, r.usable[0].amount?.minor, "a debit must be negative")
        assertEquals(250000L, r.usable[1].amount?.minor)
    }

    @Test
    fun `semicolon delimited continental export`() {
        val csv = """
            Datum;Beschreibung;Betrag;Waehrung
            20.08.2026;Supermarkt;-12,50;EUR
            21.08.2026;Gehalt;2.500,00;EUR
        """.trimIndent()

        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(2, r.usable.size)
        assertEquals(-1250L, r.usable[0].amount?.minor)
        assertEquals(250000L, r.usable[1].amount?.minor, "1.234,56 style grouping")
    }

    @Test
    fun `indian export with lakh grouping`() {
        val csv = """
            Txn Date,Narration,Withdrawal Amt,Deposit Amt,Closing Balance
            25/08/26,UPI-CHAIWALA,500.00,,45000.00
            26/08/26,SALARY CREDIT,,"1,23,456.78",168456.78
        """.trimIndent()

        val r = CsvImport.parse(csv, Currency.INR)
        assertEquals(2, r.usable.size)
        assertEquals(-50000L, r.usable[0].amount?.minor)
        assertEquals(12345678L, r.usable[1].amount?.minor)
    }

    @Test
    fun `quoted fields containing the delimiter`() {
        val csv = """
            Date,Description,Amount
            2026-08-20,"TESCO STORES, DUBLIN",-12.50
        """.trimIndent()

        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(1, r.usable.size)
        assertEquals("TESCO STORES, DUBLIN", r.usable[0].description)
        assertEquals(-1250L, r.usable[0].amount?.minor)
    }

    @Test
    fun `escaped quotes inside a field`() {
        val csv = "Date,Description,Amount\n2026-08-20,\"THE \"\"OLD\"\" PUB\",-20.00"
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals("THE \"OLD\" PUB", r.usable[0].description)
    }

    @Test
    fun `accounting style parenthesised negatives`() {
        val csv = """
            Date,Description,Amount
            2026-08-20,Refundable deposit,(45.00)
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(-4500L, r.usable[0].amount?.minor)
    }

    @Test
    fun `preamble rows before the real header are skipped`() {
        val csv = """
            Account Statement
            Account: IE12 BOFI 9000 1234 5678
            Generated: 25/08/2026

            Date,Description,Amount
            2026-08-20,Tesco,-12.50
        """.trimIndent()

        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(1, r.usable.size)
        assertTrue(r.skippedLines > 0)
    }

    @Test
    fun `column order does not matter`() {
        val csv = """
            Amount,Date,Description
            -12.50,2026-08-20,Tesco
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(-1250L, r.usable[0].amount?.minor)
        assertEquals("Tesco", r.usable[0].description)
    }

    @Test
    fun `unrecognisable file fails loudly rather than importing nothing quietly`() {
        val r = CsvImport.parse("some notes\nnothing useful here", Currency.EUR)
        assertEquals(0, r.usable.size)
        assertTrue(r.needsReview.any { it.problems.any { p -> p.startsWith("fatal") } })
    }

    @Test
    fun `rows with problems are surfaced, not dropped`() {
        val csv = """
            Date,Description,Amount
            2026-08-20,Good row,-12.50
            2026-08-21,Bad row,not-a-number
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(1, r.usable.size)
        assertEquals(1, r.needsReview.size, "a row that fails to parse must be reported")
        assertNotNull(r.needsReview.first().description)
    }

    @Test
    fun `both debit and credit populated is refused rather than guessed`() {
        val csv = """
            Date,Description,Debit,Credit
            2026-08-20,Ambiguous,10.00,5.00
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(0, r.usable.size)
        assertTrue(r.needsReview.first().problems.any { it.contains("both") })
    }

    @Test
    fun `empty file does not crash`() {
        assertEquals(0, CsvImport.parse("", Currency.EUR).rows.size)
    }
}
