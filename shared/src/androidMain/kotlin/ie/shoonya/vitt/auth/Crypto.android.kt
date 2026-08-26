package ie.shoonya.vitt.auth

import java.security.MessageDigest
import java.security.SecureRandom

actual object Crypto {
    // Android's SecureRandom is seeded from the kernel CSPRNG.
    private val random = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { random.nextBytes(it) }

    actual fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)
}
