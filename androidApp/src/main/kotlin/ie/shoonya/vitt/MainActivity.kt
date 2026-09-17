package ie.shoonya.vitt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
        if (resumedOnce) {
            BrowserAuth.onCancelled()
            // Back from elsewhere: the moment another device's entries are
            // most likely waiting. The launch itself syncs from AppRoot.
            services?.sync?.onForeground()
        }
        resumedOnce = true
    }

    private var resumedOnce = false
    private var services: VittServices? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ie.shoonya.vitt.auth.initTokenStore(applicationContext)
        ie.shoonya.vitt.sync.initInstallMarker(applicationContext)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The launch switches iOS reads from the environment. Android processes
        // do not get one, so they arrive as intent extras:
        //
        //   adb shell am start -n ie.shoonya.vitt/.MainActivity --ez seed true
        //
        // Development only, and the same two seeders the iOS side calls, so a
        // screen can be compared across platforms against identical data
        // instead of against whatever was typed into each by hand.
        val services = VittServices(
            tokenStore = ie.shoonya.vitt.auth.platformTokenStore(),
            browser = BrowserAuth(applicationContext),
            now = { System.currentTimeMillis() },
            driver = ie.shoonya.vitt.sync.androidDriver(applicationContext),
        )
        this.services = services
        if (intent?.getBooleanExtra("seed", false) == true) services.seedSampleData()
        if (intent?.getBooleanExtra("stress", false) == true) services.seedStressData()

        setContent {
            // `enableEdgeToEdge` means this window draws behind the status and
            // navigation bars, and nothing in the shared UI applies an inset:
            // the "Ledgers" title sat at the same height as the clock. iOS
            // never showed it because Compose Multiplatform applies the safe
            // area there itself, so this is the one place the two platforms
            // genuinely differ and it belongs here rather than in commonMain,
            // where it would pad iOS twice.
            //
            // Status bar only. The bottom is the tab bar's own generous
            // padding, and insetting the root would also inset the bottom
            // sheets, which are meant to reach the edge.
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.windowInsetsPadding(
                    WindowInsets.statusBars,
                ),
            ) {
                AppRoot(services)
            }
        }
    }
}
