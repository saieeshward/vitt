package ie.shoonya.vitt.auth

/**
 * The two primitives PKCE needs that Kotlin has no common implementation for.
 *
 * Both are `expect` rather than hand-rolled deliberately. A hand-written SHA-256
 * would be slower and easier to get subtly wrong, and there is no safe way to
 * write a cryptographic RNG in pure Kotlin — a fallback to a pseudo-random
 * source would make the PKCE verifier guessable and silently defeat the
 * protection it exists to provide.
 */
expect object Crypto {
    /** Cryptographically secure random bytes from the platform CSPRNG. */
    fun randomBytes(size: Int): ByteArray

    fun sha256(input: ByteArray): ByteArray
}

/** Creates a PKCE pair using platform cryptography. */
fun newPkcePair(): PkcePair =
    PkcePair.fromRandomBytes(Crypto.randomBytes(64), Crypto::sha256)

/** An unguessable `state` value for CSRF protection on the redirect. */
fun newAuthState(): String = PkcePair.base64UrlNoPadding(Crypto.randomBytes(24))
