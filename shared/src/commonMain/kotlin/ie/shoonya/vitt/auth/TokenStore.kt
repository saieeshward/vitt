package ie.shoonya.vitt.auth

/**
 * Where OAuth tokens live.
 *
 * An interface rather than a platform class, so that [AuthManager] depends on
 * the behaviour and not on the Keychain. That is not architectural taste: the
 * platform implementations cannot run in a unit-test process — a test binary has
 * no keychain entitlement — so tying the manager to them makes its logic
 * untestable on the platform it actually ships to.
 */
interface TokenStore {
    /** @throws TokenStoreException if the credential could not be written. */
    fun save(tokens: StoredTokens)
    fun load(): StoredTokens?
    fun clear()

    /**
     * Holds the in-progress sign-in across the hop to the browser.
     *
     * Android launches the consent page in a separate task, and the host process
     * is routinely killed while it is foreground on a low-memory device. Keeping
     * the PKCE verifier only in a coroutine frame means the redirect comes back
     * to a process that can no longer complete the exchange — sign-in simply
     * never finishes, and does so most often on the cheapest phones.
     */
    fun savePending(pending: PendingAuth?)
    fun loadPending(): PendingAuth?
}

/**
 * The half of a sign-in that must outlive the browser hop.
 *
 * The verifier is a secret for the duration of the flow — anyone holding it and
 * the intercepted code could complete the exchange — so it lives in the same
 * protected store as the tokens, never in ordinary preferences.
 */
@kotlinx.serialization.Serializable
data class PendingAuth(
    val verifier: String,
    val state: String,
    val startedAtMillis: Long,
) {
    /** A flow left open for hours is abandoned, not resumed. */
    fun isStale(nowMillis: Long): Boolean = nowMillis - startedAtMillis > MAX_AGE_MILLIS

    companion object { const val MAX_AGE_MILLIS = 30 * 60 * 1000L }
}

/**
 * A credential could not be stored or read.
 *
 * Deliberately loud. Silently failing to persist a refresh token signs the user
 * out at the next launch with no explanation, long after the cause.
 */
class TokenStoreException(message: String, val status: Int? = null) : Exception(message)

/** Non-persistent. For tests, and for the JVM target, which never ships. */
class InMemoryTokenStore : TokenStore {
    private var tokens: StoredTokens? = null
    private var pending: PendingAuth? = null
    override fun save(tokens: StoredTokens) { this.tokens = tokens }
    override fun load(): StoredTokens? = tokens
    override fun clear() { tokens = null; pending = null }
    override fun savePending(pending: PendingAuth?) { this.pending = pending }
    override fun loadPending(): PendingAuth? = pending
}

/** The platform's real credential store. */
expect fun platformTokenStore(): TokenStore

@kotlinx.serialization.Serializable
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String?,
    /** Epoch millis. Compared against a supplied clock, never a global one. */
    val expiresAtMillis: Long,
    val email: String? = null,
    /**
     * The spreadsheet this account is bound to.
     *
     * Kept beside the credential rather than only in the local database because
     * the two have different lifetimes: on iOS the Keychain survives deleting
     * the app while the database does not. A reinstall therefore came back
     * signed in with no idea which file was its own — and `drive.file` grants no
     * search, so the app could not look for it and would silently create a
     * second one, splitting the user's history in two.
     */
    val spreadsheetId: String? = null,
) {
    /**
     * Treats a token as expired slightly early, so a request is not sent with a
     * token that dies in flight — which would surface as a spurious auth failure
     * mid-sync.
     */
    fun isExpired(nowMillis: Long, skewMillis: Long = DEFAULT_SKEW_MILLIS): Boolean =
        nowMillis >= expiresAtMillis - skewMillis

    companion object {
        const val DEFAULT_SKEW_MILLIS = 60_000L
    }
}
