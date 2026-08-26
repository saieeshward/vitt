package ie.shoonya.vitt.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs

class AuthFlowTest {

    private val clientId = "123456-abcdef.apps.googleusercontent.com"
    private val pkce = PkcePair(verifier = "v".repeat(64), challenge = "chal-abc")

    @Test
    fun `requests only non-sensitive scopes`() {
        // This is the decision the whole no-verification strategy rests on.
        // `spreadsheets` is sensitive and `drive` is restricted; either would
        // trigger Google review plus a recurring paid security assessment, and
        // neither buys anything for files the app created itself.
        assertEquals(
            listOf(
                "https://www.googleapis.com/auth/drive.file",
                "openid",
                "email",
                "profile",
            ),
            AuthFlow.SCOPES,
        )
        val url = AuthFlow.authorizationUrl(clientId, pkce, "state-1")
        assertFalse("auth%2Fspreadsheets" in url, "must never request the sensitive scope")
        assertFalse("auth%2Fdrive&" in url, "must never request full drive")
        assertFalse("auth%2Fdrive.readonly" in url)
    }

    @Test
    fun `authorization url carries pkce and asks for a refresh token`() {
        val url = AuthFlow.authorizationUrl(clientId, pkce, "state-1")
        assertTrue("code_challenge=chal-abc" in url)
        assertTrue("code_challenge_method=S256" in url, "plain would defeat PKCE entirely")
        assertTrue("response_type=code" in url)
        assertTrue("state=state-1" in url)
        // Without offline access there is no refresh token, so the user would be
        // re-authenticating roughly every hour.
        assertTrue("access_type=offline" in url)
        assertTrue("prompt=consent" in url)
    }

    @Test
    fun `redirect uri is the reversed client id`() {
        assertEquals(
            "com.googleusercontent.apps.123456-abcdef:/oauth2redirect",
            AuthFlow.redirectUri(clientId),
        )
    }

    @Test
    fun `token request sends the verifier and no secret`() {
        val body = AuthFlow.tokenRequestBody(clientId, "auth-code", "verifier-xyz")
        assertTrue("code_verifier=verifier-xyz" in body)
        assertTrue("grant_type=authorization_code" in body)
        assertFalse("client_secret" in body, "native apps are public clients")
    }

    @Test
    fun `refresh request omits the secret too`() {
        val body = AuthFlow.refreshRequestBody(clientId, "refresh-abc")
        assertTrue("grant_type=refresh_token" in body)
        assertTrue("refresh_token=refresh-abc" in body)
        assertFalse("client_secret" in body)
    }

    @Test
    fun `successful redirect yields the code`() {
        val result = AuthFlow.parseRedirect(
            "com.googleusercontent.apps.x:/oauth2redirect?state=abc&code=4%2F0Ab_c",
            expectedState = "abc",
        )
        assertEquals("4/0Ab_c", assertIs<AuthResult.Code>(result).code)
    }

    @Test
    fun `a mismatched state is rejected`() {
        // Without this check a forged redirect could plant an attacker's code,
        // and the app would sync the user's finances into someone else's Drive.
        val result = AuthFlow.parseRedirect(
            "app:/oauth2redirect?state=WRONG&code=4%2F0Ab_c",
            expectedState = "abc",
        )
        assertTrue("state mismatch" in assertIs<AuthResult.Failed>(result).reason)
    }

    @Test
    fun `a missing state is rejected`() {
        val result = AuthFlow.parseRedirect("app:/oauth2redirect?code=xyz", expectedState = "abc")
        assertIs<AuthResult.Failed>(result)
    }

    @Test
    fun `user cancellation is not an error`() {
        // Surfacing "authentication failed" when someone simply backed out is
        // both wrong and alarming in a finance app.
        val result = AuthFlow.parseRedirect(
            "app:/oauth2redirect?error=access_denied&state=abc",
            expectedState = "abc",
        )
        assertIs<AuthResult.Cancelled>(result)
    }

    @Test
    fun `other errors are reported with their description`() {
        val result = AuthFlow.parseRedirect(
            "app:/oauth2redirect?error=invalid_scope&error_description=Bad%20scope&state=abc",
            expectedState = "abc",
        )
        assertEquals("Bad scope", assertIs<AuthResult.Failed>(result).reason)
    }

    @Test
    fun `a redirect with no query fails cleanly`() {
        assertIs<AuthResult.Failed>(AuthFlow.parseRedirect("app:/oauth2redirect", "abc"))
    }
}

class PkceTest {

    /** Deterministic stand-in; the real hash is supplied by the platform. */
    private fun fakeSha(bytes: ByteArray): ByteArray =
        ByteArray(32) { i -> (bytes.getOrElse(i) { 0 } + i.toByte()).toByte() }

    @Test
    fun `verifier length is within the spec`() {
        val pkce = PkcePair.fromRandomBytes(ByteArray(64) { it.toByte() }, ::fakeSha)
        assertTrue(pkce.verifier.length in PkcePair.MIN_VERIFIER_LENGTH..PkcePair.MAX_VERIFIER_LENGTH)
    }

    @Test
    fun `verifier uses only unreserved characters`() {
        val pkce = PkcePair.fromRandomBytes(ByteArray(64) { (it * 7).toByte() }, ::fakeSha)
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        assertTrue(pkce.verifier.all { it in allowed }, "got '${pkce.verifier}'")
    }

    @Test
    fun `method is always S256`() {
        assertEquals("S256", PkcePair.fromRandomBytes(ByteArray(64), ::fakeSha).method)
    }

    @Test
    fun `weak entropy is refused`() {
        // Silently accepting a short seed would leave PKCE guessable.
        var threw = false
        try {
            PkcePair.fromRandomBytes(ByteArray(8), ::fakeSha)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "must refuse fewer than 32 bytes of entropy")
    }

    @Test
    fun `base64url has no padding or unsafe characters`() {
        val encoded = PkcePair.base64UrlNoPadding(byteArrayOf(1, 2, 3, 4, 5))
        assertFalse('=' in encoded, "padding is forbidden by RFC 7636")
        assertFalse('+' in encoded)
        assertFalse('/' in encoded)
    }

    @Test
    fun `base64url matches known vectors`() {
        assertEquals("", PkcePair.base64UrlNoPadding(byteArrayOf()))
        assertEquals("AQ", PkcePair.base64UrlNoPadding(byteArrayOf(1)))
        assertEquals("AQI", PkcePair.base64UrlNoPadding(byteArrayOf(1, 2)))
        assertEquals("AQID", PkcePair.base64UrlNoPadding(byteArrayOf(1, 2, 3)))
        // 0xFB 0xFF exercises the two characters that differ from standard base64.
        assertEquals("-_8", PkcePair.base64UrlNoPadding(byteArrayOf(-5, -1)))
    }
}
