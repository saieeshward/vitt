package ie.shoonya.vitt.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIScene
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
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
 *
 * **Two references here are load-bearing and both are easy to lose.** The
 * session must be retained until its completion handler fires, and
 * `presentationContextProvider` is a **weak** property, so the anchor must be
 * retained by something else. Holding either only in the coroutine frame means
 * it is freed the moment `authorize` suspends — and the failure is silent: no
 * sheet, no error, no callback, a button that appears to do nothing.
 */
@OptIn(ExperimentalForeignApi::class)
actual class BrowserAuth {

    /**
     * Held for the lifetime of this object rather than the call.
     *
     * UIKit stores `presentationContextProvider` weakly, so a local would be
     * deallocated as soon as the suspending block returns, leaving the system
     * with nothing to ask for a window when it goes to present.
     */
    private val anchorProvider = PresentationAnchorProvider()

    /** Retains the in-flight session; cleared by the completion handler. */
    private var activeSession: ASWebAuthenticationSession? = null

    actual suspend fun authorize(url: String, redirectScheme: String): AuthResult =
        suspendCancellableCoroutine { continuation ->
            // A second call while one is outstanding would strand the first.
            activeSession?.cancel()

            val session = ASWebAuthenticationSession(
                uRL = NSURL(string = url),
                callbackURLScheme = redirectScheme,
            ) { callbackUrl, error ->
                activeSession = null
                when {
                    callbackUrl != null ->
                        continuation.resume(AuthResult.Code(callbackUrl.absoluteString ?: ""))
                    // Dismissing the sheet arrives here as an error. Reporting
                    // that as a failure would be both wrong and alarming.
                    error != null -> continuation.resume(AuthResult.Cancelled)
                    else -> continuation.resume(AuthResult.Failed("no callback and no error"))
                }
            }

            activeSession = session
            session.presentationContextProvider = anchorProvider
            session.prefersEphemeralWebBrowserSession = false

            // start() returning false means the session could not present at
            // all. Left unchecked it looks identical to a user who has not
            // finished signing in yet — the coroutine simply never resumes.
            if (!session.start()) {
                activeSession = null
                continuation.resume(AuthResult.Failed("could not present the sign-in sheet"))
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                activeSession = null
                session.cancel()
            }
        }
}

@OptIn(ExperimentalForeignApi::class)
private class PresentationAnchorProvider :
    NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {

    /**
     * The window to present over.
     *
     * A freshly constructed `UIWindow` is not a usable fallback — an unattached
     * window never presents anything — so this walks the real scene graph and
     * widens its search rather than inventing one: the key window, then any
     * window of a foreground-active scene, then any window at all.
     */
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor {
        val scenes = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()

        val windows = scenes
            .sortedByDescending { it.activationState == UISceneActivationStateForegroundActive }
            .flatMap { it.windows.filterIsInstance<UIWindow>() }

        return windows.firstOrNull { it.isKeyWindow() }
            ?: windows.firstOrNull()
            ?: UIWindow()
    }
}
