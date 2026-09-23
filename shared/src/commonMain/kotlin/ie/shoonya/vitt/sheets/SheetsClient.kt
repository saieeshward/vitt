package ie.shoonya.vitt.sheets

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
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
    /**
     * Forces a new access token, returning null if re-authentication is needed.
     *
     * Needed because a token can die without expiring: Google revokes grants
     * server-side, and the stored expiry still says the token is fine. Without a
     * way to invalidate on a 401, sync stops permanently and looks like a
     * network fault.
     */
    private val forceRefresh: suspend () -> String? = { null },
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
            http.post("$SHEETS_BASE/spreadsheets/$spreadsheetId/values/${encodePathSegment(tab)}:append") {
                auth()
                parameter("valueInputOption", "RAW")
                parameter("insertDataOption", "INSERT_ROWS")
                contentType(ContentType.Application.Json)
                setBody(ValueRange(values = rows))
            }
        }

    /**
     * Replaces a range with typed values, and clears whatever was below it.
     *
     * This is the derived-tab write, and it is a replace rather than an append
     * because a derived tab *is* the current state rather than a history of it.
     * The clear is the half that is easy to forget: a month where three rows
     * were deleted writes a shorter table, and without it the old tail stays on
     * screen as rows that no longer exist anywhere else.
     *
     * Order matters. The write goes first and the clear second, targeting only
     * what is past the new end: clearing first would leave the tab empty for as
     * long as the second call takes, and a person looking at it in that window
     * sees their spreadsheet wiped.
     *
     * `RAW`, as everywhere else here. [Cell.Number] is what gets a real number
     * past it; see [TypedValueRange].
     */
    suspend fun replaceValues(
        spreadsheetId: String,
        tab: String,
        rows: List<List<Cell>>,
        /** The last column of the block being written, e.g. `J`. */
        lastColumn: Char,
    ): UpdateValuesResponse {
        val written: UpdateValuesResponse = request {
            http.put(
                "$SHEETS_BASE/spreadsheets/$spreadsheetId/values/" +
                    encodePathSegment("$tab!A1:$lastColumn${rows.size.coerceAtLeast(1)}")
            ) {
                auth()
                parameter("valueInputOption", "RAW")
                contentType(ContentType.Application.Json)
                setBody(TypedValueRange(values = rows.map { row -> row.map(Cell::toJson) }))
            }
        }
        clearBelow(spreadsheetId, tab, firstRow = rows.size + 1, lastColumn = lastColumn)
        return written
    }

    /**
     * Empties everything from [firstRow] down.
     *
     * A closed range, like every other read and write here, because an open one
     * walks to the end of the grid. The bound is generous rather than exact —
     * clearing rows that are already empty costs nothing, and guessing too low
     * leaves the stale tail this call exists to remove.
     */
    suspend fun clearBelow(
        spreadsheetId: String,
        tab: String,
        firstRow: Int,
        lastColumn: Char,
        lastRow: Int = firstRow + CLEAR_SPAN,
    ) {
        request<ClearValuesResponse> {
            http.post(
                "$SHEETS_BASE/spreadsheets/$spreadsheetId/values/" +
                    encodePathSegment("$tab!A$firstRow:$lastColumn$lastRow") + ":clear"
            ) {
                auth()
                contentType(ContentType.Application.Json)
                setBody(EmptyBody)
            }
        }
    }

    /** Reads a closed range. Never pass an open range like `A:F` — see below. */
    suspend fun read(spreadsheetId: String, range: String): List<List<String>> =
        request<ValueRangeResponse> {
            http.get("$SHEETS_BASE/spreadsheets/$spreadsheetId/values/${encodePathSegment(range)}") {
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
    /** Rows read, and the spreadsheet row number the next read should start at. */
    data class Page(val rows: List<List<String>>, val nextRow: Int)

    suspend fun readPaged(
        spreadsheetId: String,
        tab: String,
        columns: String,
        pageSize: Int = 5_000,
        startRow: Int = 2,
    ): Page {
        val all = mutableListOf<List<String>>()
        var row = startRow
        while (true) {
            val last = row + pageSize - 1
            val page = read(spreadsheetId, "$tab!${columns.first()}$row:${columns.last()}$last")
            all += page
            // A short page means the end of the data, since Sheets trims
            // trailing empty rows.
            if (page.size < pageSize) return Page(all, row + page.size)
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
        val response = send(block)
        if (response.status.isSuccess()) return response.body()

        // One retry on 401, and only on 401. The token may have been revoked
        // server-side while still looking valid locally; anything else that
        // returns 401 twice is genuinely a re-authentication.
        if (response.status.value == 401 && forceRefresh() != null) {
            val retried = send(block)
            if (retried.status.isSuccess()) return retried.body()
            throw retried.toError()
        }
        throw response.toError()
    }

    private suspend inline fun send(block: () -> HttpResponse): HttpResponse = try {
        block()
    } catch (e: SheetsError) {
        throw e
    } catch (e: CancellationException) {
        // M7: cancellation is not a network failure. Wrapping it as one made a
        // user navigating away mid-sync look like an outage, incrementing the
        // attempt count toward poisoning.
        throw e
    } catch (e: Throwable) {
        throw SheetsError.Transport(e.message ?: "request failed", e)
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
                // 403 is overloaded: quota exhaustion and permission denial share
                // it. Newer RESOURCE_EXHAUSTED responses carry an empty errors[]
                // and put the signal in status/message instead, so keying only on
                // reason told the user their spreadsheet was gone every time they
                // hit the write ceiling — and poisoned the batch.
                reason.contains("rateLimit", true) ||
                    reason.contains("quota", true) ||
                    parsed?.status == "RESOURCE_EXHAUSTED" ||
                    message.contains("quota", true) ||
                    message.contains("rate limit", true) ->
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

        /**
         * How far past the new last row a replace clears.
         *
         * Covers the realistic case — a month's worth of rows deleted at once —
         * without the cost of clearing to the bottom of the grid. A larger
         * deletion than this leaves a tail that the next write trims further,
         * which is worth more than a slow call on every sync.
         */
        const val CLEAR_SPAN = 5_000
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

/**
 * Percent-encodes a value going into the URL path.
 *
 * A1 notation quotes any tab name containing a space (`'My Tab'!A2:F50`), and a
 * `#` in a raw URL becomes a fragment — so a user renaming a tab, which the
 * design explicitly invites, otherwise breaks reads and writes as a 400. A 400
 * is classified non-retryable, so it would poison the batch rather than surface
 * as "your tab was renamed".
 */
internal fun encodePathSegment(value: String): String = buildString {
    value.encodeToByteArray().forEach { b ->
        val c = b.toInt().toChar()
        if (c.isLetterOrDigit() || c in "-._~!$&()*+,;=:@") append(c)
        else append('%').append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
    }
}
