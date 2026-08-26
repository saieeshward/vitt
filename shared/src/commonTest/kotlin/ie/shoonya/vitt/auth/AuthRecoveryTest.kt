package ie.shoonya.vitt.auth

import ie.shoonya.vitt.sheets.SheetsClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The ways a session breaks that are not simple expiry.
 */
class AuthRecoveryTest {

    private val clientId = "123-abc.apps.googleusercontent.com"
    private fun json() = headersOf(HttpHeaders.ContentType, "application/json")

    // ---- H1: a token can die without expiring -----------------------------

    @Test
    fun `a 401 forces one refresh and retries`() = runTest {
        // Google revokes grants server-side; the stored expiry still says fine.
        var calls = 0
        val engine = MockEngine { _ ->
            calls++
            if (calls == 1) respondError(HttpStatusCode.Unauthorized)
            else respond("""{"values":[["ok"]]}""", HttpStatusCode.OK, json())
        }
        var refreshed = false
        val sheets = SheetsClient(
            http = SheetsClient.configure(HttpClient(engine)),
            accessToken = { "stale" },
            forceRefresh = { refreshed = true; "fresh" },
        )
        assertEquals(listOf(listOf("ok")), sheets.read("s", "A1:A1"))
        assertEquals(true, refreshed, "a 401 must trigger a refresh, not just fail")
        assertEquals(2, calls, "exactly one retry")
    }

    @Test
    fun `a second 401 is a genuine re-authentication`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respondError(HttpStatusCode.Unauthorized) }
        val sheets = SheetsClient(
            http = SheetsClient.configure(HttpClient(engine)),
            accessToken = { "stale" },
            forceRefresh = { "fresh" },
        )
        assertFailsWith<SheetsErrorUnauthorizedAlias> { sheets.read("s", "A1:A1") }
        assertEquals(2, calls, "must not retry forever")
    }

    @Test
    fun `without a refresh hook a 401 is not retried`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respondError(HttpStatusCode.Unauthorized) }
        val sheets = SheetsClient(SheetsClient.configure(HttpClient(engine)), accessToken = { "t" })
        assertFailsWith<SheetsErrorUnauthorizedAlias> { sheets.read("s", "A1:A1") }
        assertEquals(1, calls)
    }

    // ---- H4: the flow must outlive the process ----------------------------

    @Test
    fun `a sign-in survives the process that started it`() = runTest {
        // Android kills the host process while Chrome is foreground. The
        // redirect then arrives at a different process entirely.
        val store = InMemoryTokenStore()
        val started = AuthManager(
            http = SheetsClient.configure(HttpClient(MockEngine { respondError(HttpStatusCode.OK) })),
            tokens = store, browser = BrowserAuth(), clientId = clientId, now = { 1_000L },
        )
        // Simulate the first process getting as far as opening the browser.
        val pkce = newPkcePair()
        store.savePending(PendingAuth(pkce.verifier, "state-xyz", 1_000L))

        // A *new* AuthManager, as a fresh process would build.
        val engine = MockEngine {
            respond("""{"access_token":"a","refresh_token":"r","expires_in":3600}""",
                HttpStatusCode.OK, json())
        }
        val resumed = AuthManager(
            http = SheetsClient.configure(HttpClient(engine)),
            tokens = store, browser = BrowserAuth(), clientId = clientId, now = { 2_000L },
        )
        val result = resumed.completeRedirect("app:/oauth2redirect?state=state-xyz&code=abc")
        assertIs<AuthResult.Code>(result)
        assertNotNull(store.load(), "the token must be stored by whichever process finishes")
        assertNull(store.loadPending(), "and the pending record cleared")
    }

    @Test
    fun `a redirect with no pending flow is refused`() {
        // Protects against a forged or replayed redirect arriving unsolicited.
        val store = InMemoryTokenStore()
        val auth = AuthManager(
            http = SheetsClient.configure(HttpClient(MockEngine { respondError(HttpStatusCode.OK) })),
            tokens = store, browser = BrowserAuth(), clientId = clientId, now = { 1_000L },
        )
        kotlinx.coroutines.test.runTest {
            val r = auth.completeRedirect("app:/oauth2redirect?state=x&code=y")
            assertIs<AuthResult.Failed>(r)
        }
    }

    @Test
    fun `an abandoned sign-in expires rather than resuming hours later`() = runTest {
        val store = InMemoryTokenStore()
        store.savePending(PendingAuth("v", "state-xyz", startedAtMillis = 0L))
        val auth = AuthManager(
            http = SheetsClient.configure(HttpClient(MockEngine { respondError(HttpStatusCode.OK) })),
            tokens = store, browser = BrowserAuth(), clientId = clientId,
            now = { PendingAuth.MAX_AGE_MILLIS + 1 },
        )
        val r = auth.completeRedirect("app:/oauth2redirect?state=state-xyz&code=abc")
        assertIs<AuthResult.Failed>(r)
        assertNull(store.loadPending())
    }

    // ---- H5: the spreadsheet binding outlives the local database ----------

    @Test
    fun `the spreadsheet binding survives a reinstall`() = runTest {
        // On iOS the Keychain survives deleting the app; the database does not.
        // Without this the app comes back signed in, unable to search under
        // drive.file, and would create a second spreadsheet.
        val store = InMemoryTokenStore()
        store.save(StoredTokens("a", "r", expiresAtMillis = 10_000_000))
        val auth = AuthManager(
            http = SheetsClient.configure(HttpClient(MockEngine { respondError(HttpStatusCode.OK) })),
            tokens = store, browser = BrowserAuth(), clientId = clientId, now = { 0L },
        )
        auth.rememberSpreadsheet("sheet-123")
        assertEquals("sheet-123", auth.boundSpreadsheet())
    }

    @Test
    fun `a refresh does not lose the spreadsheet binding`() = runTest {
        val store = InMemoryTokenStore()
        store.save(StoredTokens("old", "r", expiresAtMillis = 1_000, spreadsheetId = "sheet-123"))
        val engine = MockEngine {
            respond("""{"access_token":"fresh","expires_in":3600}""", HttpStatusCode.OK, json())
        }
        val auth = AuthManager(
            http = SheetsClient.configure(HttpClient(engine)),
            tokens = store, browser = BrowserAuth(), clientId = clientId, now = { 500_000L },
        )
        auth.accessToken()
        assertEquals("sheet-123", auth.boundSpreadsheet(), "re-auth must not orphan the sheet")
    }
}

private typealias SheetsErrorUnauthorizedAlias = ie.shoonya.vitt.sheets.SheetsError.Unauthorized
