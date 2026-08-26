package ie.shoonya.vitt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.auth.TokenStore
import ie.shoonya.vitt.ui.AppRoot

/**
 * The Android entry point. Mirrors iOS's `MainViewController`: it hosts the
 * shared Compose UI and owns nothing else.
 */
class MainActivity : ComponentActivity() {
    /**
     * The OAuth redirect arrives as a new Intent because the Activity is
     * singleTask. Without forwarding it, the coroutine waiting on the browser
     * never completes and sign-in appears to hang.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { BrowserAuth.onRedirect(it) }
    }

    /**
     * Resuming without a redirect means the user backed out of the browser.
     *
     * Android has no callback for that — unlike iOS, where dismissing the sheet
     * surfaces as an error — so without this the coroutine waits forever for a
     * redirect that will never arrive, and every control stays disabled until
     * the app is force-quit.
     */
    override fun onResume() {
        super.onResume()
        if (resumedOnce) BrowserAuth.onCancelled()
        resumedOnce = true
    }

    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ie.shoonya.vitt.auth.initTokenStore(applicationContext)
        ie.shoonya.vitt.sync.initInstallMarker(applicationContext)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot(
                VittServices(
                    tokenStore = ie.shoonya.vitt.auth.platformTokenStore(),
                    browser = BrowserAuth(applicationContext),
                    now = { System.currentTimeMillis() },
                )
            )
        }
    }
}
