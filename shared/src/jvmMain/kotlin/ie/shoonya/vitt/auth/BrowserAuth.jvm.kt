package ie.shoonya.vitt.auth

/**
 * Not implemented: the JVM target exists for unit tests, and a browser-based
 * consent flow is inherently interactive. Live verification against Google runs
 * on a real device.
 */
actual class BrowserAuth {
    actual suspend fun authorize(url: String, redirectScheme: String): AuthResult =
        AuthResult.Failed("browser sign-in is not available on the JVM target")
}
