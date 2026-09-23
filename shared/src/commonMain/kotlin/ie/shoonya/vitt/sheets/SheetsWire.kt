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

/**
 * A value range whose cells keep their type.
 *
 * Separate from [ValueRange] rather than replacing it, because the two write
 * different things for different reasons. The `Events` log is strings all the
 * way down by design — a tagged-string encoding that survives any spreadsheet
 * without the app trusting how it was stored. A derived tab is the opposite: a
 * person reads it, and a column of money that is text sums to nothing.
 *
 * [JsonUnquotedLiteral] is what makes an exact decimal reach the wire. The
 * obvious alternative, `JsonPrimitive(Double)`, would put every amount in this
 * app through a float on the way out of it — after all the care taken to keep
 * money in integer minor units, and for no gain: the value is already a decimal
 * string and JSON's number type is textual.
 */
@Serializable
data class TypedValueRange(
    val range: String? = null,
    val majorDimension: String = "ROWS",
    val values: List<List<kotlinx.serialization.json.JsonPrimitive>>,
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

// ---- values.update / values.clear --------------------------------------------

/** `values.clear` takes an empty object and returns the range it emptied. */
@Serializable
object EmptyBody

@Serializable
data class ClearValuesResponse(
    val spreadsheetId: String? = null,
    val clearedRange: String? = null,
)

/**
 * A [Cell] as it goes onto the wire.
 *
 * Text becomes a JSON string and stays inert under `RAW`; a number becomes a
 * JSON number written verbatim, so `-12.50` reaches the sheet as the value
 * −12.50 without ever having been a Double. A blank is the empty string, which
 * is what clears a cell.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal fun Cell.toJson(): kotlinx.serialization.json.JsonPrimitive = when (this) {
    is Cell.Text -> kotlinx.serialization.json.JsonPrimitive(value)
    is Cell.Number -> kotlinx.serialization.json.JsonUnquotedLiteral(plain)
    Cell.Blank -> kotlinx.serialization.json.JsonPrimitive("")
    is Cell.Bool -> kotlinx.serialization.json.JsonPrimitive(value)
}

/**
 * A read that keeps each cell's type: `valueRenderOption=UNFORMATTED_VALUE`.
 *
 * The derived tabs are read this way and the event log is not. A formatted read
 * returns what the cell *displays*, so `-12.50` written as a number comes back
 * as `-12.5`, and as `€12.50` once somebody formats the column as currency —
 * which is the first thing anybody does to a column of money. Compared against
 * what the app wrote, every one of those reads as an edit, and the tab stops
 * updating the second time it is refreshed.
 */
@Serializable
data class TypedValueRangeResponse(
    val range: String? = null,
    val values: List<List<kotlinx.serialization.json.JsonElement>> = emptyList(),
)

/**
 * A cell as an unformatted read returns it.
 *
 * A number keeps the spelling Google sent, never passing through a Double here.
 * An empty string is a blank, because that is how the API reports one inside a
 * row.
 */
internal fun cellOf(element: kotlinx.serialization.json.JsonElement): Cell {
    val primitive = element as? kotlinx.serialization.json.JsonPrimitive ?: return Cell.Text(element.toString())
    return when {
        primitive is kotlinx.serialization.json.JsonNull -> Cell.Blank
        primitive.isString -> if (primitive.content.isEmpty()) Cell.Blank else Cell.Text(primitive.content)
        primitive.content == "true" -> Cell.Bool(true)
        primitive.content == "false" -> Cell.Bool(false)
        else -> Cell.Number(primitive.content)
    }
}

// ---- spreadsheets.batchUpdate ------------------------------------------------

@Serializable
data class BatchUpdateRequest(val requests: List<SheetRequest>)

@Serializable
data class SheetRequest(
    val addSheet: AddSheetRequest? = null,
    val duplicateSheet: DuplicateSheetRequest? = null,
    val deleteDimension: DeleteDimensionRequest? = null,
    val addChart: AddChartRequest? = null,
)

// ---- charts ------------------------------------------------------------------
// Only the fields a column chart over a closed range needs.

@Serializable
data class AddChartRequest(val chart: EmbeddedChart)

@Serializable
data class EmbeddedChart(val spec: ChartSpec, val position: EmbeddedObjectPosition)

@Serializable
data class ChartSpec(val title: String, val basicChart: BasicChartSpec)

@Serializable
data class BasicChartSpec(
    val chartType: String = "COLUMN",
    val legendPosition: String = "BOTTOM_LEGEND",
    val axis: List<BasicChartAxis>,
    val domains: List<BasicChartDomain>,
    val series: List<BasicChartSeries>,
    /** The first row of the range is the labels, not a month. */
    val headerCount: Int = 1,
)

@Serializable
data class BasicChartAxis(val position: String, val title: String? = null)

@Serializable
data class BasicChartDomain(val domain: ChartData)

@Serializable
data class BasicChartSeries(val series: ChartData, val targetAxis: String = "LEFT_AXIS")

@Serializable
data class ChartData(val sourceRange: ChartSourceRange)

@Serializable
data class ChartSourceRange(val sources: List<GridRange>)

/** Zero-based, end-exclusive. */
@Serializable
data class GridRange(
    val sheetId: Int,
    val startRowIndex: Int,
    val endRowIndex: Int,
    val startColumnIndex: Int,
    val endColumnIndex: Int,
)

@Serializable
data class EmbeddedObjectPosition(val overlayPosition: OverlayPosition)

@Serializable
data class OverlayPosition(val anchorCell: GridCoordinate, val widthPixels: Int, val heightPixels: Int)

@Serializable
data class GridCoordinate(val sheetId: Int, val rowIndex: Int, val columnIndex: Int)

@Serializable
data class DeleteDimensionRequest(val range: DimensionRange)

/** Zero-based and end-exclusive, as the API counts. */
@Serializable
data class DimensionRange(
    val sheetId: Int,
    val dimension: String = "ROWS",
    val startIndex: Int,
    val endIndex: Int,
)

// ---- values.batchUpdate ------------------------------------------------------

@Serializable
data class BatchValuesRequest(
    val valueInputOption: String = "RAW",
    val data: List<TypedValueRange>,
)

@Serializable
data class BatchValuesResponse(val totalUpdatedCells: Int? = null)

@Serializable
data class AddSheetRequest(val properties: SheetProperties)

@Serializable
data class BatchUpdateResponse(val spreadsheetId: String? = null)

@Serializable
data class DuplicateSheetRequest(
    val sourceSheetId: Int,
    val newSheetName: String,
    val insertSheetIndex: Int? = null,
)
