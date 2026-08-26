package ie.shoonya.vitt.sheets

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the client against a mocked transport.
 *
 * This verifies the request VITT *sends* and how it interprets what comes back.
 * It cannot verify that Google behaves as modelled — that needs a live run
 * against a real spreadsheet, which is tracked separately.
 */
class SheetsClientTest {

    private fun client(handler: MockRequestHandler): Pair<SheetsClient, MockEngine> {
        val engine = MockEngine(handler)
        val http = SheetsClient.configure(HttpClient(engine))
        return SheetsClient(http, accessToken = { "test-token" }) to engine
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    // ---- requests we send ---------------------------------------------------

    @Test
    fun `append never overwrites and never reinterprets values`() = runTest {
        val (sheets, engine) = client {
            respond("""{"updates":{"updatedRange":"Events!A2:F2","updatedRows":1}}""", HttpStatusCode.OK, jsonHeaders())
        }
        sheets.append("sheet-1", "Events", listOf(listOf("a", "b")))

        val url = engine.requestHistory.single().url.toString()
        assertTrue("insertDataOption=INSERT_ROWS" in url,
            "OVERWRITE is the API default and would destroy anything below the table: $url")
        assertTrue("valueInputOption=RAW" in url,
            "USER_ENTERED would reinterpret values by spreadsheet locale: $url")
        assertTrue(":append" in url)
    }

    @Test
    fun `requests carry the bearer token`() = runTest {
        val (sheets, engine) = client { respond("""{"values":[]}""", HttpStatusCode.OK, jsonHeaders()) }
        sheets.read("sheet-1", "Events!A2:F10")
        assertEquals("Bearer test-token", engine.requestHistory.single().headers["Authorization"])
    }

    @Test
    fun `created spreadsheet pins locale and timezone`() = runTest {
        val (sheets, engine) = client {
            respond("""{"spreadsheetId":"new-1"}""", HttpStatusCode.OK, jsonHeaders())
        }
        val created = sheets.createSpreadsheet("VITT", listOf("Events", "Transactions"))
        assertEquals("new-1", created.spreadsheetId)

        val body = (engine.requestHistory.single().body as TextContent).text
        // Sheets stores dates as serial numbers and parses by locale, so an
        // unpinned locale makes date and decimal handling vary per user.
        assertTrue("locale" in body, body)
        assertTrue("Etc/UTC" in body, body)
    }

    @Test
    fun `file version request asks only for the fields it needs`() = runTest {
        val (sheets, engine) = client {
            respond("""{"id":"f1","version":"42","modifiedTime":"2026-08-26T00:00:00Z"}""",
                HttpStatusCode.OK, jsonHeaders())
        }
        val file = sheets.fileVersion("f1")
        assertEquals("42", file.version)
        assertTrue("fields=" in engine.requestHistory.single().url.toString())
    }

    // ---- responses we interpret ---------------------------------------------

    @Test
    fun `empty range yields no rows rather than failing`() = runTest {
        // Sheets omits `values` entirely for an empty range.
        val (sheets, _) = client { respond("""{"range":"Events!A2:F10"}""", HttpStatusCode.OK, jsonHeaders()) }
        assertEquals(emptyList(), sheets.read("s", "Events!A2:F10"))
    }

    @Test
    fun `unknown response fields are ignored`() = runTest {
        // Google adds fields over time; a strict parser would break on upgrade.
        val (sheets, _) = client {
            respond("""{"values":[["a"]],"somethingNew":{"x":1}}""", HttpStatusCode.OK, jsonHeaders())
        }
        assertEquals(listOf(listOf("a")), sheets.read("s", "Events!A2:F10"))
    }

    @Test
    fun `paging stops on a short page`() = runTest {
        var call = 0
        val (sheets, _) = client {
            call++
            val rows = if (call == 1) List(3) { listOf("r$it") } else emptyList()
            respond(
                """{"values":[${rows.joinToString(",") { """["${it[0]}"]""" }}]}""",
                HttpStatusCode.OK, jsonHeaders(),
            )
        }
        val page = sheets.readPaged("s", "Events", "AF", pageSize = 3)
        assertEquals(3, page.rows.size)
        assertEquals(5, page.nextRow, "the cursor advances past what was read")
        assertEquals(2, call, "one full page, then one short page")
    }

    // ---- error classification ------------------------------------------------

    @Test
    fun `401 is not retryable`() = runTest {
        val (sheets, _) = client { respondError(HttpStatusCode.Unauthorized) }
        val e = assertFailsWith<SheetsError.Unauthorized> { sheets.read("s", "A1:A1") }
        assertFalse(e.retryable, "retrying with the same dead token cannot help")
    }

    @Test
    fun `429 is retryable and honours Retry-After`() = runTest {
        val (sheets, _) = client {
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "30"))
        }
        val e = assertFailsWith<SheetsError.RateLimited> { sheets.read("s", "A1:A1") }
        assertTrue(e.retryable)
        assertEquals(30, e.retryAfterSeconds)
    }

    @Test
    fun `403 quota is rate limiting - 403 permission is not`() = runTest {
        // 403 is overloaded; treating a quota error as permanent would drop data,
        // and treating a permission error as transient would spin forever.
        val (quota, _) = client {
            respond(
                """{"error":{"code":403,"message":"Quota exceeded","errors":[{"reason":"rateLimitExceeded"}]}}""",
                HttpStatusCode.Forbidden, jsonHeaders(),
            )
        }
        assertTrue(assertFailsWith<SheetsError.RateLimited> { quota.read("s", "A1:A1") }.retryable)

        val (denied, _) = client {
            respond(
                """{"error":{"code":403,"message":"The caller does not have permission","errors":[{"reason":"forbidden"}]}}""",
                HttpStatusCode.Forbidden, jsonHeaders(),
            )
        }
        assertFalse(assertFailsWith<SheetsError.FileNotAccessible> { denied.read("s", "A1:A1") }.retryable)
    }

    @Test
    fun `5xx is retryable`() = runTest {
        val (sheets, _) = client { respondError(HttpStatusCode.ServiceUnavailable) }
        assertTrue(assertFailsWith<SheetsError.Server> { sheets.read("s", "A1:A1") }.retryable)
    }

    @Test
    fun `404 means the file is gone and cannot be searched for`() = runTest {
        // drive.file grants no list or search, so recovery is asking the user
        // to pick the file again, not looking for it.
        val (sheets, _) = client { respondError(HttpStatusCode.NotFound) }
        assertFalse(assertFailsWith<SheetsError.FileNotAccessible> { sheets.read("s", "A1:A1") }.retryable)
    }

    @Test
    fun `400 is a bug and is not retried`() = runTest {
        val (sheets, _) = client {
            respond("""{"error":{"code":400,"message":"Invalid range"}}""",
                HttpStatusCode.BadRequest, jsonHeaders())
        }
        val e = assertFailsWith<SheetsError.BadRequest> { sheets.read("s", "A1:A1") }
        assertFalse(e.retryable)
        assertTrue("Invalid range" in e.message!!)
    }

    @Test
    fun `network failure is transport and retryable`() = runTest {
        val (sheets, _) = client { throw IOException("connection reset") }
        assertTrue(assertFailsWith<SheetsError.Transport> { sheets.read("s", "A1:A1") }.retryable)
    }

    @Test
    fun `an unparseable error body still classifies by status`() = runTest {
        val (sheets, _) = client { respond("<html>502 Bad Gateway</html>", HttpStatusCode.BadGateway) }
        assertTrue(assertFailsWith<SheetsError.Server> { sheets.read("s", "A1:A1") }.retryable)
    }
}
