package ie.shoonya.vitt.auth

/**
 * In-memory only. The JVM target exists for fast unit tests, not as a shipping
 * platform, and writing a real credential to disk unencrypted — even in a test
 * harness — is a habit worth not forming.
 */
actual fun platformTokenStore(): TokenStore = InMemoryTokenStore()
