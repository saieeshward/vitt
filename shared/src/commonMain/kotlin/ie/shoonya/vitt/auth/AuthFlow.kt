package ie.shoonya.vitt.auth

/**
 * Builds the Google OAuth requests and interprets the redirect.
 *
 * Pure string handling, no I/O: the browser hand-off and token storage differ
 * per platform, but everything decided here — scopes, PKCE, state validation —
 * is identical and belongs in one tested place.
 *
 * **Scopes are the load-bearing decision.** All four requested here are
 * *non-sensitive*, which is what keeps VITT out of Google's verification review
 * and the recurring CASA assessment. `drive.file` grants access only to files
 * the app created or the user explicitly picked — never their wider Drive.
 */
object AuthFlow {

    const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"

    val SCOPES = listOf(
        "https://www.googleapis.com/auth/drive.file",
        "openid",
        "email",
        "profile",
    )

    /**
     * iOS redirects to the client id with its dot-separated parts reversed;
     * Android uses the same scheme. Neither needs registering with Google beyond
     * the client itself.
     */
    fun redirectUri(clientId: String): String {
        val bare = clientId.removeSuffix(".apps.googleusercontent.com")
        return "com.googleusercontent.apps.$bare:/oauth2redirect"
    }

    fun authorizationUrl(
        clientId: String,
        pkce: PkcePair,
        state: String,
        loginHint: String? = null,
    ): String {
        val params = buildList {
            add("client_id" to clientId)
            add("redirect_uri" to redirectUri(clientId))
            add("response_type" to "code")
            add("scope" to SCOPES.joinToString(" "))
            add("code_challenge" to pkce.challenge)
            add("code_challenge_method" to pkce.method)
            add("state" to state)
            // Without these Google returns no refresh token on repeat consent,
            // and the user would have to re-authenticate every hour.
            add("access_type" to "offline")
            add("prompt" to "consent")
            loginHint?.let { add("login_hint" to it) }
        }
        return AUTH_ENDPOINT + "?" + params.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
    }

    /** Form body for redeeming the authorization code. Public client: no secret. */
    fun tokenRequestBody(clientId: String, code: String, verifier: String): String =
        listOf(
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to verifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to redirectUri(clientId),
        ).joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

    fun refreshRequestBody(clientId: String, refreshToken: String): String =
        listOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        ).joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

    /**
     * Parses the redirect.
     *
     * The `state` check is not a formality: without it a forged redirect could
     * plant an attacker's authorization code, and the app would helpfully sync
     * the user's finances into someone else's Drive.
     */
    fun parseRedirect(uri: String, expectedState: String): AuthResult {
        val query = uri.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return AuthResult.Failed("no query in redirect")
        val params = query.split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i < 0) null else decode(pair.substring(0, i)) to decode(pair.substring(i + 1))
        }.toMap()

        params["error"]?.let { err ->
            return if (err == "access_denied") AuthResult.Cancelled
            else AuthResult.Failed(params["error_description"] ?: err)
        }

        val state = params["state"]
        if (state == null || state != expectedState) {
            return AuthResult.Failed("state mismatch: possible forged redirect")
        }
        val code = params["code"] ?: return AuthResult.Failed("no code in redirect")
        return AuthResult.Code(code)
    }

    private fun encode(s: String): String = buildString {
        s.encodeToByteArray().forEach { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() || c in "-._~") append(c)
            else append('%').append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }

    private fun decode(s: String): String {
        val out = StringBuilder()
        var i = 0
        val plus = s.replace('+', ' ')
        while (i < plus.length) {
            val c = plus[i]
            if (c == '%' && i + 2 < plus.length) {
                out.append(plus.substring(i + 1, i + 3).toInt(16).toChar()); i += 3
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}

sealed interface AuthResult {
    data class Code(val code: String) : AuthResult
    /** The user backed out. Not an error; must not surface as one. */
    data object Cancelled : AuthResult
    data class Failed(val reason: String) : AuthResult
}
