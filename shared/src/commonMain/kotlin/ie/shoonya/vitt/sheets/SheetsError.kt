package ie.shoonya.vitt.sheets

/**
 * What went wrong, and — the part that matters — whether retrying could help.
 *
 * Getting this classification wrong is expensive in both directions: retrying a
 * permanent failure spins forever, while giving up on a transient one loses a
 * transaction the user believes was saved.
 */
sealed class SheetsError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    abstract val retryable: Boolean

    /** No network, DNS failure, connection dropped. Always worth retrying. */
    class Transport(message: String, cause: Throwable? = null) : SheetsError(message, cause) {
        override val retryable = true
    }

    /**
     * Over quota. Sheets allows 60 write requests per minute per user, and a
     * batch counts as one — so this should be rare, but backoff must honour
     * `Retry-After` when present.
     */
    class RateLimited(val retryAfterSeconds: Long?) :
        SheetsError("rate limited${retryAfterSeconds?.let { ", retry after ${it}s" } ?: ""}") {
        override val retryable = true
    }

    /** 5xx. Google's problem, not ours. */
    class Server(val status: Int, message: String) : SheetsError("server error $status: $message") {
        override val retryable = true
    }

    /** Token expired or revoked. Not retryable until re-auth; never loses queued work. */
    class Unauthorized(message: String) : SheetsError("unauthorized: $message") {
        override val retryable = false
    }

    /**
     * The app no longer has access to this file — the user revoked it, deleted
     * the spreadsheet, or the OAuth client changed. Under `drive.file` the app
     * cannot search for it either, so recovery means asking the user to pick the
     * file again.
     */
    class FileNotAccessible(val fileId: String, message: String) :
        SheetsError("cannot access $fileId: $message") {
        override val retryable = false
    }

    /** Malformed request. Retrying identical input cannot help; this is a bug. */
    class BadRequest(val status: Int, message: String) : SheetsError("bad request $status: $message") {
        override val retryable = false
    }
}
