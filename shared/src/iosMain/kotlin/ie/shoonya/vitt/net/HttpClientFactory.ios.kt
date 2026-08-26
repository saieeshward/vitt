package ie.shoonya.vitt.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData

/**
 * The iOS engine, with the URL cache switched off.
 *
 * `NSURLSession` caches GET responses by default, and a `Cache-Control: no-cache`
 * *request* header does not reliably stop it serving one. Two polls of the same
 * Drive URL then returned byte-identical bodies — the same `modifiedTime` to the
 * millisecond — which is indistinguishable from "the file did not change" and
 * made change detection silently useless.
 *
 * Nothing this app fetches is worth caching: every request asks whether
 * something changed, which is exactly the question a cache cannot answer.
 */
actual fun platformHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureRequest {
            setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        }
    }
}
