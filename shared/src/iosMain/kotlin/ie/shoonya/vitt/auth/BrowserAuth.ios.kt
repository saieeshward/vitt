package ie.shoonya.vitt.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * `ASWebAuthenticationSession` is the system-provided sign-in flow: the page runs
 * outside the app's process, so VITT never sees the password, and it shares
 * cookies with Safari so an already-signed-in user is one tap away.
 *
 * An embedded `WKWebView` would be the easy alternative and is not an option —
 * Google rejects OAuth from embedded webviews with `disallowed_useragent`,
 * because the hosting app could read what is typed into it.
 */
@OptIn(ExperimentalForeignApi::class)
actual class BrowserAuth {

    actual suspend fun authorize(url: String, redirectScheme: String): AuthResult =
        suspendCancellableCoroutine { continuation ->
            // Held in a local so it survives until the callback fires; without a
            // strong reference the session is deallocated and never presents.
            var session: ASWebAuthenticationSession? = null
            val anchor = PresentationAnchorProvider()

            session = ASWebAuthenticationSession(
                uRL = NSURL(string = url),
                callbackURLScheme = redirectScheme,
            ) { callbackUrl, error ->
                when {
                    callbackUrl != null ->
                        continuation.resume(AuthResult.Code(callbackUrl.absoluteString ?: ""))
                    // Dismissing the sheet arrives here as an error. Reporting
                    // that as a failure would be both wrong and alarming.
                    error != null -> continuation.resume(AuthResult.Cancelled)
                    else -> continuation.resume(AuthResult.Failed("no callback and no error"))
                }
                session = null
            }

            // Mandatory since iOS 13. Without an anchor the session has no window
            // to attach to and simply never appears — no error, no sheet.
            session?.presentationContextProvider = anchor
            session?.prefersEphemeralWebBrowserSession = false
            session?.start()
            continuation.invokeOnCancellation { session?.cancel() }
        }
}

@OptIn(ExperimentalForeignApi::class)
private class PresentationAnchorProvider :
    NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {

    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<platform.UIKit.UIWindowScene>()
        .firstOrNull()
        ?.windows
        ?.filterIsInstance<UIWindow>()
        ?.firstOrNull { it.isKeyWindow() }
        ?: UIWindow()
}
