package ie.shoonya.vitt.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import ie.shoonya.vitt.sheets.SheetsClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthManagerTest {

    private val clientId = "123-abc.apps.googleusercontent.com"

    private fun manager(
        store: TokenStore = InMemoryTokenStore(),
        now: () -> Long = { 0L },
        handler: io.ktor.client.engine.mock.MockRequestHandler,
    ): Pair<AuthManager, MockEngine> {
        val engine = MockEngine(handler)
        val http = SheetsClient.configure(HttpClient(engine))
        return AuthManager(http, store, BrowserAuth(), clientId, now) to engine
    }

    private fun json() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `a valid token is returned without contacting Google`() = runTest {
        val store = InMemoryTokenStore().apply {
            save(StoredTokens("live-token", "refresh", expiresAtMillis = 10_000_000))
        }
        val (auth, engine) = manager(store, now = { 0L }) {
            respond("", HttpStatusCode.InternalServerError)
        }
        assertEquals("live-token", auth.accessToken())
        assertEquals(0, engine.requestHistory.size, "no refresh needed, so no request")
    }

    @Test
    fun `an expired token is refreshed`() = runTest {
        val store = InMemoryTokenStore().apply {
            save(StoredTokens("old", "refresh-1", expiresAtMillis = 1_000))
        }
        val (auth, engine) = manager(store, now = { 500_000L }) {
            respond("""{"access_token":"fresh","expires_in":3600}""", HttpStatusCode.OK, json())
        }
        assertEquals("fresh", auth.accessToken())
        val body = (engine.requestHistory.single().body as TextContent).text
        assertTrue("grant_type=refresh_token" in body, body)
        assertTrue("refresh_token=refresh-1" in body, body)
    }

    @Test
    fun `refresh keeps the existing refresh token when Google omits it`() = runTest {
        // Google usually does omit it. Overwriting with null would sign the user
        // out on their next launch.
        val store = InMemoryTokenStore().apply {
            save(StoredTokens("old", "refresh-1", expiresAtMillis = 1_000))
        }
        val (auth, _) = manager(store, now = { 500_000L }) {
            respond("""{"access_token":"fresh","expires_in":3600}""", HttpStatusCode.OK, json())
        }
        auth.accessToken()
        assertEquals("refresh-1", store.load()?.refreshToken)
    }

    @Test
    fun `a rejected refresh token is cleared rather than retried forever`() = runTest {
        val store = InMemoryTokenStore().apply {
            save(StoredTokens("old", "revoked", expiresAtMillis = 1_000))
        }
        val (auth, _) = manager(store, now = { 500_000L }) {
            respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, json())
        }
        assertNull(auth.accessToken())
        assertNull(store.load(), "a dead credential must not be kept and retried")
    }

    @Test
    fun `a server error during refresh does not discard the credential`() = runTest {
        // Google being down is temporary; deleting the refresh token over it
        // would force an unnecessary re-consent.
        val store = InMemoryTokenStore().apply {
            save(StoredTokens("old", "refresh-1", expiresAtMillis = 1_000))
        }
        val (auth, _) = manager(store, now = { 500_000L }) {
            respond("", HttpStatusCode.ServiceUnavailable)
        }
        assertNull(auth.accessToken())
        assertEquals("refresh-1", store.load()?.refreshToken, "outage must not sign the user out")
    }

    @Test
    fun `refresh is single-flight`() = runTest {
        // Several queued syncs waking together would otherwise each redeem the
        // refresh token; Google caps live tokens and invalidates the oldest, so
        // a stampede can sign the user out of their own account.
        val store = InMemoryTokenStore().apply {
            save(StoredTokens("old", "refresh-1", expiresAtMillis = 1_000))
        }
        var calls = 0
        val (auth, _) = manager(store, now = { 500_000L }) {
            calls++
            respond("""{"access_token":"fresh-$calls","expires_in":3600}""", HttpStatusCode.OK, json())
        }
        val results = listOf(auth.accessToken(), auth.accessToken(), auth.accessToken())
        assertEquals(1, calls, "three callers, one refresh")
        assertTrue(results.all { it == "fresh-1" })
    }

    @Test
    fun `no stored token means signed out — not an error`() = runTest {
        val (auth, _) = manager { respond("", HttpStatusCode.OK) }
        assertNull(auth.accessToken())
    }

    @Test
    fun `sign out clears the local credential even if revoke fails`() = runTest {
        val store = InMemoryTokenStore().apply {
            save(StoredTokens("a", "r", expiresAtMillis = 10_000_000))
        }
        val (auth, _) = manager(store) { respond("", HttpStatusCode.InternalServerError) }
        auth.signOut()
        assertNull(store.load(), "the user is signed out regardless of Google's reply")
    }
}
