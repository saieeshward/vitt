package ie.shoonya.vitt.auth

/**
 * Proof Key for Code Exchange (RFC 7636).
 *
 * Mobile apps are *public clients*: they ship no secret, because anything in the
 * binary can be extracted. That leaves the authorization code exposed — a
 * malicious app registering the same redirect URI could intercept it and trade
 * it for tokens.
 *
 * PKCE closes that. The client invents a random `code_verifier`, sends only its
 * SHA-256 hash up front, and reveals the verifier when redeeming the code. An
 * interceptor holding just the code cannot produce the verifier, so the exchange
 * fails.
 *
 * S256 only. The spec permits `plain`, which sends the verifier in the clear and
 * defeats the entire mechanism.
 */
data class PkcePair(
    val verifier: String,
    val challenge: String,
) {
    val method: String get() = "S256"

    companion object {
        const val MIN_VERIFIER_LENGTH = 43
        const val MAX_VERIFIER_LENGTH = 128

        /** RFC 7636 §4.1: unreserved characters only. */
        private const val ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /**
         * @param randomBytes cryptographically secure random bytes, supplied by
         * the platform. Passed in rather than generated here so the algorithm
         * stays pure and testable, and so no weak fallback can creep in.
         */
        fun fromRandomBytes(randomBytes: ByteArray, sha256: (ByteArray) -> ByteArray): PkcePair {
            require(randomBytes.size >= 32) {
                "need at least 32 bytes of entropy, got ${randomBytes.size}"
            }
            val verifier = randomBytes.map { ALPHABET[(it.toInt() and 0xFF) % ALPHABET.length] }
                .joinToString("")
                .take(MAX_VERIFIER_LENGTH)
            require(verifier.length >= MIN_VERIFIER_LENGTH)
            val challenge = base64UrlNoPadding(sha256(verifier.encodeToByteArray()))
            return PkcePair(verifier, challenge)
        }

        /**
         * Base64url without padding, per RFC 7636 §4.2. Standard base64 would be
         * rejected: `+` and `/` are not URL-safe and `=` padding is forbidden here.
         */
        fun base64UrlNoPadding(bytes: ByteArray): String {
            val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            val sb = StringBuilder()
            var i = 0
            while (i + 2 < bytes.size) {
                val n = ((bytes[i].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                    (bytes[i + 2].toInt() and 0xFF)
                sb.append(table[(n shr 18) and 63]).append(table[(n shr 12) and 63])
                    .append(table[(n shr 6) and 63]).append(table[n and 63])
                i += 3
            }
            when (bytes.size - i) {
                1 -> {
                    val n = (bytes[i].toInt() and 0xFF) shl 16
                    sb.append(table[(n shr 18) and 63]).append(table[(n shr 12) and 63])
                }
                2 -> {
                    val n = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                    sb.append(table[(n shr 18) and 63]).append(table[(n shr 12) and 63])
                        .append(table[(n shr 6) and 63])
                }
            }
            return sb.toString()
        }
    }
}
