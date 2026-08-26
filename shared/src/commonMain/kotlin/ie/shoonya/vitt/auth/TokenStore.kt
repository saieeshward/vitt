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
    override fun save(tokens: StoredTokens) { this.tokens = tokens }
    override fun load(): StoredTokens? = tokens
    override fun clear() { tokens = null }
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
