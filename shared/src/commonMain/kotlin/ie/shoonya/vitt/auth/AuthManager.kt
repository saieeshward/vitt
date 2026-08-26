package ie.shoonya.vitt.auth

import ie.shoonya.vitt.sheets.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the user's Google session: sign-in, token refresh, sign-out.
 *
 * Two properties matter more than the mechanics.
 *
 * **A dead token must never block local use.** VITT works fully offline, so a
 * revoked or expired credential is a sync problem, not an app problem. Nothing
 * here throws in a way that should reach a screen the user is typing into.
 *
 * **Refresh is single-flight.** Several queued syncs waking at once would
 * otherwise each notice the expiry and each redeem the refresh token. Google
 * caps live tokens per client and starts invalidating the oldest, so a
 * refresh stampede can sign the user out of their own account.
 */
class AuthManager(
    private val http: HttpClient,
    private val tokens: TokenStore,
    private val browser: BrowserAuth,
    private val clientId: String,
    private val now: () -> Long,
) {
    private val refreshLock = Mutex()

    val isSignedIn: Boolean get() = tokens.load() != null

    /**
     * Runs the full consent flow. Returns [AuthResult.Cancelled] if the user
     * backs out — an ordinary outcome that must not surface as an error.
     */
    suspend fun signIn(): AuthResult {
        val pkce = newPkcePair()
        val state = newAuthState()
        val url = AuthFlow.authorizationUrl(clientId, pkce, state)
        val scheme = AuthFlow.redirectUri(clientId).substringBefore(":")

        return when (val result = browser.authorize(url, scheme)) {
            is AuthResult.Cancelled -> result
            is AuthResult.Failed -> result
            is AuthResult.Code -> {
                // The browser hands back the whole redirect URI; the state check
                // lives in parseRedirect and is what stops a forged callback.
                when (val parsed = AuthFlow.parseRedirect(result.code, state)) {
                    is AuthResult.Code -> exchange(parsed.code, pkce.verifier)
                    else -> parsed
                }
            }
        }
    }

    private suspend fun exchange(code: String, verifier: String): AuthResult {
        val response = http.post(AuthFlow.TOKEN_ENDPOINT) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(AuthFlow.tokenRequestBody(clientId, code, verifier))
        }
        if (!response.ok()) return AuthResult.Failed(response.errorText())

        val body: TokenResponse = response.body()
        if (body.refreshToken == null) {
            // Without a refresh token every session dies in an hour. This means
            // access_type=offline or prompt=consent went missing.
            return AuthResult.Failed("no refresh token returned; consent flow is misconfigured")
        }
        tokens.save(
            StoredTokens(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                expiresAtMillis = now() + body.expiresIn * 1000,
            )
        )
        return AuthResult.Code(code)
    }

    /**
     * A valid access token, refreshing if needed.
     *
     * Returns null when the user must sign in again. Callers treat that as "sync
     * is paused", never as data loss — the outbox keeps accumulating and drains
     * once access is restored.
     */
    suspend fun accessToken(): String? = refreshLock.withLock {
        val stored = tokens.load() ?: return null
        if (!stored.isExpired(now())) return stored.accessToken

        val refresh = stored.refreshToken ?: return null
        val response = http.post(AuthFlow.TOKEN_ENDPOINT) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(AuthFlow.refreshRequestBody(clientId, refresh))
        }
        if (!response.ok()) {
            // A 400 here means the refresh token is dead — revoked, or expired
            // after long disuse. Clearing avoids retrying a credential that can
            // never work again.
            if (response.status.value == 400) tokens.clear()
            return null
        }

        val body: TokenResponse = response.body()
        val updated = stored.copy(
            accessToken = body.accessToken,
            // Google usually omits the refresh token on refresh; keeping the old
            // one is required, and overwriting with null would sign the user out.
            refreshToken = body.refreshToken ?: stored.refreshToken,
            expiresAtMillis = now() + body.expiresIn * 1000,
        )
        tokens.save(updated)
        updated.accessToken
    }

    /**
     * Revokes access with Google and erases the local copy.
     *
     * Apple's Guideline 5.1.1 requires an in-app way to disconnect a linked
     * account, so this is a store requirement as well as good manners. It does
     * not touch the user's spreadsheet — that file is theirs.
     */
    suspend fun signOut() {
        val stored = tokens.load()
        tokens.clear()
        val token = stored?.refreshToken ?: stored?.accessToken ?: return
        runCatching {
            http.post(AuthFlow.REVOKE_ENDPOINT) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("token=$token")
            }
        }
        // A failed revoke is deliberately ignored: the local credential is
        // already gone, so the user is signed out either way.
    }

    private fun HttpResponse.ok() = status.value in 200..299

    private suspend fun HttpResponse.errorText(): String =
        runCatching { bodyAsText().take(300) }.getOrDefault("HTTP ${status.value}")
}
