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
    fun `rows with problems are surfaced - not dropped`() {
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

/**
 * The three silent-corruption paths: input that produced a plausible but wrong
 * number instead of a rejection.
 */
class CsvCorruptionTest {

    @Test
    fun `an unsupported currency is refused, never stamped as the default`() {
        // A Wise export with a CHF row: importing it as EUR 340 is exactly the
        // currency blending the product exists to refuse.
        val csv = """
            Date,Description,Amount,Currency
            2026-08-20,Zurich hotel,-340.00,CHF
            2026-08-21,Dublin lunch,-12.50,EUR
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(1, r.usable.size, "only the EUR row is importable")
        assertEquals(-1250L, r.usable.single().amount?.minor)
        assertTrue(r.needsReview.single().problems.any { it.contains("CHF") })
    }

    @Test
    fun `a trailing minus is a debit, not income`() {
        // German and Austrian convention. Previously the digit filter stripped
        // the sign and every debit imported as income.
        val csv = """
            Buchungstag;Verwendungszweck;Betrag
            20.08.2026;Supermarkt;1.234,56-
            21.08.2026;Gehalt;2.500,00
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(2, r.usable.size)
        assertEquals(-123456L, r.usable[0].amount?.minor, "trailing minus means debit")
        assertEquals(250000L, r.usable[1].amount?.minor)
    }

    @Test
    fun `DR and CR suffixes set the direction`() {
        val csv = """
            Date,Narration,Amount
            25/08/26,UPI-CHAIWALA,500.00 DR
            26/08/26,SALARY,45000.00 CR
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.INR)
        assertEquals(-50000L, r.usable[0].amount?.minor)
        assertEquals(4500000L, r.usable[1].amount?.minor)
    }

    @Test
    fun `a preamble line does not decide the delimiter`() {
        // The killer case: a non-delimited first line made every candidate score
        // equally, comma won the tie, and a semicolon file parsed as one column
        // whose "amount" was the whole row with non-digits stripped.
        val csv = """
            Kontoauszug 08/2026
            Konto DE89 3704 0044 0532 0130 00
            Buchungstag;Verwendungszweck;Betrag
            20.08.2026;Supermarkt;-12,50
            21.08.2026;Tankstelle;-60,00
        """.trimIndent()
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(';', CsvImport.detectDelimiter(csv.lines()))
        assertEquals(2, r.usable.size)
        assertEquals(-1250L, r.usable[0].amount?.minor, "must not fabricate from the whole line")
        assertEquals(-6000L, r.usable[1].amount?.minor)
    }

    @Test
    fun `a file whose columns cannot be separated fails loudly`() {
        // Better to refuse than to import invented amounts.
        val csv = "Datum Betrag Beschreibung\n20.08.2026 12,50 Supermarkt"
        val r = CsvImport.parse(csv, Currency.EUR)
        assertEquals(0, r.usable.size)
        assertTrue(r.needsReview.any { row -> row.problems.any { it.startsWith("fatal") } })
    }
}
