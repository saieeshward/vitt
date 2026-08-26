package ie.shoonya.vitt.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredTokensTest {

    private fun tokens(expiresAt: Long) = StoredTokens(
        accessToken = "at",
        refreshToken = "rt",
        expiresAtMillis = expiresAt,
    )

    @Test
    fun `a token is treated as expired slightly before it actually is`() {
        // Sending a request with a token that dies in flight surfaces as a
        // spurious auth failure mid-sync, so expiry is anticipated.
        val expiresAt = 100_000L
        assertFalse(tokens(expiresAt).isExpired(nowMillis = 30_000))
        assertTrue(
            tokens(expiresAt).isExpired(nowMillis = 60_000),
            "within the skew window it must already count as expired",
        )
    }

    @Test
    fun `an expired token is expired`() {
        assertTrue(tokens(100_000).isExpired(nowMillis = 200_000))
    }

    @Test
    fun `skew is configurable for tests that need exactness`() {
        assertFalse(tokens(100_000).isExpired(nowMillis = 99_000, skewMillis = 0))
        assertTrue(tokens(100_000).isExpired(nowMillis = 100_000, skewMillis = 0))
    }
}
