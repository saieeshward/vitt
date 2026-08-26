package ie.shoonya.vitt.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Custom Tabs sign-in.
 *
 * Custom Tabs runs the page in the user's own browser process, so it carries
 * their existing Google session and VITT never sees the password. An embedded
 * WebView would be rejected by Google (`disallowed_useragent`) and would put the
 * app in a position to read the credentials.
 *
 * Android has no suspending equivalent of iOS's completion handler: the redirect
 * arrives as a new Intent on the Activity. The Activity forwards it here through
 * [onRedirect], which is why this holds a pending result.
 */
actual class BrowserAuth(private val context: Context) {

    actual suspend fun authorize(url: String, redirectScheme: String): AuthResult {
        val deferred = CompletableDeferred<AuthResult>()
        pending = deferred

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Ask for a Custom Tab; falls back to a plain browser if none of the
            // installed browsers support it.
            putExtra("androidx.browser.customtabs.extra.SESSION", null as android.os.Bundle?)
            putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2)
        }
        context.startActivity(intent)

        return deferred.await()
    }

    companion object {
        private var pending: CompletableDeferred<AuthResult>? = null

        /**
         * Called by the Activity that receives the redirect intent.
         *
         * Returns true if the redirect was consumed, so the Activity can tell a
         * genuine auth callback apart from an unrelated deep link.
         */
        fun onRedirect(uri: String): Boolean {
            val waiting = pending ?: return false
            pending = null
            waiting.complete(AuthResult.Code(uri))
            return true
        }

        /**
         * Called when the Activity resumes without a redirect, which is what
         * backing out of the browser looks like on Android. Without this the
         * coroutine would hang forever waiting for a callback that never comes.
         */
        fun onCancelled() {
            pending?.complete(AuthResult.Cancelled)
            pending = null
        }
    }
}
