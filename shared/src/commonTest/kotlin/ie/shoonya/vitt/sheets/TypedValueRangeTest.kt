package ie.shoonya.vitt.sheets

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypedValueRangeTest {

    private val json = Json { encodeDefaults = true }

    private fun encode(vararg cells: Cell): String =
        json.encodeToString(TypedValueRange(values = listOf(cells.map(Cell::toJson))))

    @Test
    fun `money goes onto the wire as a number and keeps every digit`() {
        // The whole reason TypedValueRange exists. As a string this sums to
        // nothing in the sheet; through a Double it would not be this value.
        assertTrue("""[-12.50]""" in encode(Cell.Number("-12.50")), encode(Cell.Number("-12.50")))
    }

    @Test
    fun `a large amount is not rounded or put into exponent form`() {
        // 2^53 is where a Double stops being able to hold consecutive integers,
        // and a rupee balance in minor units gets there sooner than people
        // expect. Written verbatim, the size never matters.
        val big = "90071992547409.91"
        assertTrue("[$big]" in encode(Cell.Number(big)))
    }

    @Test
    fun `text goes on as a string so a formula stays a merchant name`() {
        // Under RAW a quoted string is inert. This is the injection that a
        // merchant field would otherwise carry straight into somebody's
        // spreadsheet.
        val nasty = """=cmd|' /c calc'!A1"""
        val out = encode(Cell.Text(nasty))
        // Quoted, so the sheet stores the characters rather than evaluating
        // them. The unquoted form is what Cell.Number produces, and a merchant
        // name must never reach it.
        assertTrue(""""$nasty"""" in out, out)
        assertEquals("""=cmd|' /c calc'!A1""", nasty)
    }

    @Test
    fun `a blank is an empty string rather than null`() {
        // null would be a hole in the row and shift nothing; "" is what clears
        // a cell that used to hold something.
        assertTrue("""[""]""" in encode(Cell.Blank))
    }

    @Test
    fun `a date stays text so no locale gets to reinterpret it`() {
        assertTrue(""""2026-04-03"""" in encode(Cell.Text("2026-04-03")))
    }

    @Test
    fun `a checkbox goes onto the wire as a boolean`() {
        assertTrue("""[true]""" in encode(Cell.Bool(true)), encode(Cell.Bool(true)))
    }

    @Test
    fun `an unformatted read keeps each cell's type`() {
        val read = SheetsClient.lenientJson.decodeFromString<TypedValueRangeResponse>(
            """{"range":"Transactions!A1:E1","values":[["Tesco",-12.5,true,"",90071992547409.91]]}""",
        )
        assertEquals(
            listOf(
                Cell.Text("Tesco"),
                Cell.Number("-12.5"),
                Cell.Bool(true),
                Cell.Blank,
                // Taken as Google spelled it, never through a Double.
                Cell.Number("90071992547409.91"),
            ),
            read.values.single().map(::cellOf),
        )
    }

    @Test
    fun `text that looks like a boolean stays text`() {
        // A merchant called "true" under RAW is a string, and must come back one.
        val read = SheetsClient.lenientJson.decodeFromString<TypedValueRangeResponse>("""{"values":[["true"]]}""")
        assertEquals(Cell.Text("true"), cellOf(read.values.single().single()))
    }
}
