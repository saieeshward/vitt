package ie.shoonya.vitt.auth

/**
 * Hands the user to a browser to sign in, and waits for the redirect back.
 *
 * This must be a *system* browser session — `ASWebAuthenticationSession` on iOS,
 * Custom Tabs on Android — never an embedded webview. Google refuses OAuth from
 * embedded webviews (`disallowed_useragent`), and rightly so: an app hosting the
 * webview can read what the user types into it, which defeats the point of
 * delegating authentication.
 */
expect class BrowserAuth {
    /**
     * Opens [url] and suspends until the redirect arrives or the user backs out.
     * Cancellation is an ordinary outcome here, not an error.
     */
    suspend fun authorize(url: String, redirectScheme: String): AuthResult
}
