package ie.shoonya.vitt.auth

import java.security.MessageDigest
import java.security.SecureRandom

actual object Crypto {
    private val random = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { random.nextBytes(it) }

    actual fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)
}
