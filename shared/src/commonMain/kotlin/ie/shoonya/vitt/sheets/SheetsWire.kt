package ie.shoonya.vitt.sheets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request and response shapes for the parts of the Sheets and Drive APIs VITT
 * uses. Deliberately partial — only the fields we send or read are modelled,
 * and unknown fields in responses are ignored.
 */

// ---- values.append -----------------------------------------------------------

@Serializable
data class ValueRange(
    val range: String? = null,
    val majorDimension: String = "ROWS",
    val values: List<List<String>>,
)

@Serializable
data class AppendResponse(
    val spreadsheetId: String? = null,
    /** The table as it was *before* this append — useful for spotting a concurrent write. */
    val tableRange: String? = null,
    val updates: UpdateValuesResponse? = null,
)

@Serializable
data class UpdateValuesResponse(
    val updatedRange: String? = null,
    val updatedRows: Int? = null,
    val updatedCells: Int? = null,
)

// ---- values.get --------------------------------------------------------------

@Serializable
data class ValueRangeResponse(
    val range: String? = null,
    val majorDimension: String? = null,
    // Absent entirely when the range is empty, hence the default.
    val values: List<List<String>> = emptyList(),
)

// ---- spreadsheets.create -----------------------------------------------------

@Serializable
data class CreateSpreadsheetRequest(
    val properties: SpreadsheetProperties,
    val sheets: List<SheetSpec>,
)

@Serializable
data class SpreadsheetProperties(
    val title: String,
    /**
     * Pinned deliberately. Sheets stores dates as serial numbers and parses
     * user-entered text according to the spreadsheet's locale, so `03/04/2026`
     * and `1.234` mean different things in different locales — and a mis-parse
     * is silently wrong arithmetic, not an error. Fixing the locale makes the
     * behaviour reproducible; the app writes machine-readable values anyway.
     */
    val locale: String = "en_GB",
    val timeZone: String = "Etc/UTC",
)

@Serializable
data class SheetSpec(val properties: SheetProperties)

@Serializable
data class SheetProperties(
    val title: String,
    val sheetId: Int? = null,
    val index: Int? = null,
    val hidden: Boolean? = null,
    val gridProperties: GridProperties? = null,
)

@Serializable
data class GridProperties(
    val rowCount: Int? = null,
    val columnCount: Int? = null,
    val frozenRowCount: Int? = null,
)

@Serializable
data class Spreadsheet(
    val spreadsheetId: String,
    val spreadsheetUrl: String? = null,
    val properties: SpreadsheetProperties? = null,
    val sheets: List<SheetSpec> = emptyList(),
)

// ---- Drive files.get ---------------------------------------------------------

@Serializable
data class DriveFile(
    val id: String? = null,
    val name: String? = null,
    /**
     * A monotonically increasing counter that changes on *every* server-side
     * modification, including ones the user cannot see. Cheap to poll and it
     * never misses a change, though it does report changes we caused ourselves.
     *
     * `headRevisionId` and `md5Checksum` are null for Google-native files, so
     * this is the only usable change token without a server.
     */
    val version: String? = null,
    val modifiedTime: String? = null,
    val trashed: Boolean? = null,
)

// ---- errors ------------------------------------------------------------------

@Serializable
data class GoogleErrorEnvelope(val error: GoogleError? = null)

@Serializable
data class GoogleError(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
    val errors: List<GoogleErrorDetail> = emptyList(),
)

@Serializable
data class GoogleErrorDetail(
    val domain: String = "",
    val reason: String = "",
    val message: String = "",
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String? = null,
)
