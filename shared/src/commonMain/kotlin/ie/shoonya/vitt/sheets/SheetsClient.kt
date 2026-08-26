package ie.shoonya.vitt.sheets

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The Google Sheets and Drive calls VITT makes, and nothing else.
 *
 * Scope discipline: every call here works under `drive.file` alone, which is a
 * *non-sensitive* scope. That is what keeps the project out of Google's
 * verification review and the recurring CASA security assessment. Adding any
 * call that needs `spreadsheets` or `drive` would change that, so new methods
 * belong here only if `drive.file` covers them.
 *
 * `drive.file` grants access per file, to files this app created or the user
 * explicitly picked. There is no list and no search — the caller must persist
 * the spreadsheet id.
 */
class SheetsClient(
    private val http: HttpClient,
    private val accessToken: suspend () -> String,
) {

    /**
     * Creates the spreadsheet and its tabs in one call.
     *
     * Tabs are created up front rather than lazily so the file is never
     * half-formed: a crash between "created spreadsheet" and "added Events tab"
     * would leave a file the app cannot write to and cannot find again.
     */
    suspend fun createSpreadsheet(title: String, tabs: List<String>): Spreadsheet =
        request {
            http.post("$SHEETS_BASE/spreadsheets") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(
                    CreateSpreadsheetRequest(
                        properties = SpreadsheetProperties(title = title),
                        sheets = tabs.mapIndexed { i, name ->
                            SheetSpec(
                                SheetProperties(
                                    title = name,
                                    index = i,
                                    gridProperties = GridProperties(frozenRowCount = 1),
                                )
                            )
                        },
                    )
                )
            }
        }

    /**
     * Appends rows to the end of a tab.
     *
     * `insertDataOption=INSERT_ROWS` is not optional. The default is OVERWRITE,
     * which writes over whatever sits below the detected table — and the user's
     * own notes or a second table would be silently destroyed.
     *
     * `valueInputOption=RAW` keeps values verbatim. USER_ENTERED would parse
     * them as if typed, so `1.234` and a date string would be reinterpreted
     * according to the spreadsheet's locale, and a leading `=` would become a
     * formula.
     */
    suspend fun append(spreadsheetId: String, tab: String, rows: List<List<String>>): AppendResponse =
        request {
            http.post("$SHEETS_BASE/spreadsheets/$spreadsheetId/values/$tab:append") {
                auth()
                parameter("valueInputOption", "RAW")
                parameter("insertDataOption", "INSERT_ROWS")
                contentType(ContentType.Application.Json)
                setBody(ValueRange(values = rows))
            }
        }

    /** Reads a closed range. Never pass an open range like `A:F` — see below. */
    suspend fun read(spreadsheetId: String, range: String): List<List<String>> =
        request<ValueRangeResponse> {
            http.get("$SHEETS_BASE/spreadsheets/$spreadsheetId/values/$range") {
                auth()
                parameter("majorDimension", "ROWS")
            }
        }.values

    /**
     * Reads a tab in pages.
     *
     * Ranges are always closed (`A2:F1000`), never open (`A:F`). An open-ended
     * QUERY or read walks every blank row to the end of the grid — roughly a
     * second per 20,000 empty rows — so an open range on a young spreadsheet is
     * slower than a closed one on a full one.
     */
    suspend fun readPaged(
        spreadsheetId: String,
        tab: String,
        columns: String,
        pageSize: Int = 5_000,
        startRow: Int = 2,
    ): List<List<String>> {
        val all = mutableListOf<List<String>>()
        var row = startRow
        while (true) {
            val last = row + pageSize - 1
            val page = read(spreadsheetId, "$tab!${columns.first()}$row:${columns.last()}$last")
            all += page
            if (page.size < pageSize) return all
            row = last + 1
        }
    }

    /**
     * The cheap change check: a counter that increments on every server-side
     * modification. False positives (it also counts our own writes) but never
     * false negatives, which is the right trade for a poll.
     */
    suspend fun fileVersion(fileId: String): DriveFile =
        request {
            http.get("$DRIVE_BASE/files/$fileId") {
                auth()
                parameter("fields", "id,name,version,modifiedTime,trashed")
                // Without this the platform HTTP cache answers the second poll
                // from the first one's response — NSURLSession caches GETs by
                // default — and change detection reports "nothing changed"
                // forever while happily serving a stale version and an
                // unchanged modifiedTime. The symptom looks exactly like a
                // Drive limitation, which is how it cost a debugging cycle.
                header("Cache-Control", "no-cache")
                header("Pragma", "no-cache")
            }
        }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header("Authorization", "Bearer ${accessToken()}")
    }

    private suspend inline fun <reified T> request(block: () -> HttpResponse): T {
        val response = try {
            block()
        } catch (e: SheetsError) {
            throw e
        } catch (e: Throwable) {
            throw SheetsError.Transport(e.message ?: "request failed", e)
        }
        if (response.status.isSuccess()) return response.body()
        throw response.toError()
    }

    private suspend fun HttpResponse.toError(): SheetsError {
        val raw = runCatching { bodyAsText() }.getOrDefault("")
        val parsed = runCatching {
            lenientJson.decodeFromString<GoogleErrorEnvelope>(raw).error
        }.getOrNull()
        val message = parsed?.message?.takeIf { it.isNotBlank() } ?: raw.take(300)
        val reason = parsed?.errors?.firstOrNull()?.reason.orEmpty()

        return when (status.value) {
            401 -> SheetsError.Unauthorized(message)
            403 -> when {
                // 403 is overloaded: quota exhaustion and permission denial share it.
                reason.contains("rateLimit", true) || reason.contains("quota", true) ->
                    SheetsError.RateLimited(retryAfterSeconds())
                else -> SheetsError.FileNotAccessible("unknown", message)
            }
            404 -> SheetsError.FileNotAccessible("unknown", message)
            429 -> SheetsError.RateLimited(retryAfterSeconds())
            in 500..599 -> SheetsError.Server(status.value, message)
            else -> SheetsError.BadRequest(status.value, message)
        }
    }

    private fun HttpResponse.retryAfterSeconds(): Long? =
        headers["Retry-After"]?.toLongOrNull()

    companion object {
        const val SHEETS_BASE = "https://sheets.googleapis.com/v4"
        const val DRIVE_BASE = "https://www.googleapis.com/drive/v3"

        /**
         * `encodeDefaults` is load-bearing, not a preference. Without it
         * kotlinx-serialization omits any property still equal to its default —
         * so the pinned `locale` and `timeZone` would silently never reach
         * Google, and the spreadsheet would be created in whatever locale the
         * user's account happens to use. Dates are stored as serial numbers, so
         * that mis-parse is wrong arithmetic rather than an error.
         */
        val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /** Configures a client for the Google APIs. Platform engine supplied by the caller. */
        fun configure(client: HttpClient): HttpClient = client.config {
            install(ContentNegotiation) { json(lenientJson) }
            expectSuccess = false
        }
    }
}

private fun HttpStatusCode.isSuccess() = value in 200..299
