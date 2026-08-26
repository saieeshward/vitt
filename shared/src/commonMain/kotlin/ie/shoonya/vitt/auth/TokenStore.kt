package ie.shoonya.vitt.auth

/**
 * Where OAuth tokens live.
 *
 * Tokens never touch the event log, the spreadsheet, or ordinary preferences.
 * A refresh token grants access to the user's Drive file until revoked, so it
 * belongs in platform-protected storage — Keychain on iOS, Keystore-backed on
 * Android — and nowhere else. VITT has no server, so it is never transmitted
 * anywhere either.
 */
expect class TokenStore {
    fun save(tokens: StoredTokens)
    fun load(): StoredTokens?
    fun clear()
}

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
