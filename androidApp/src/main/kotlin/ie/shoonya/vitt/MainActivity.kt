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
        // A share arriving while the app is already open. singleTask means this
        // Activity is reused rather than recreated, so onCreate never runs.
        sharedText(intent)?.let { services?.onSharedText(it) }
        if (intent.getBooleanExtra(VittWidget.EXTRA_ADD, false)) services?.requestAdd()
    }

    /**
     * The text a share or a text-selection action handed over.
     *
     * Two actions, one meaning. SEND is the share sheet; PROCESS_TEXT is the
     * selection toolbar, which is how someone captures an amount out of a
     * banking app's own screen without leaving it.
     */
    private fun sharedText(intent: android.content.Intent?): String? = when (intent?.action) {
        android.content.Intent.ACTION_SEND ->
            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        android.content.Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT)?.toString()
        else -> null
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

    /**
     * The notification permission prompt, which only an Activity can show.
     *
     * Registered unconditionally at construction because the contract requires
     * it before the Activity is started, and bridged to `:shared` so the
     * reminder switch can await a real answer instead of assuming one.
     */
    private var pendingPermission: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> pendingPermission?.complete(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ie.shoonya.vitt.auth.initTokenStore(applicationContext)
        ie.shoonya.vitt.sync.initInstallMarker(applicationContext)
        ie.shoonya.vitt.notify.initReminders(applicationContext)
        ie.shoonya.vitt.notify.initReminderPermission {
            val answer = kotlinx.coroutines.CompletableDeferred<Boolean>()
            pendingPermission = answer
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            answer.await()
        }
        ie.shoonya.vitt.widget.initWidgets(applicationContext)
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
            appVersion = BuildConfig.VERSION_NAME,
        )
        this.services = services
        //
        // Debug builds only. A release APK that still honoured these extras
        // would be a hidden feature reachable by anyone with adb, so the gate
        // is the build type rather than the extra.
        // A share that launched the app cold. The parse is offered, never saved.
        sharedText(intent)?.let { services.onSharedText(it) }
        // The widget's button. Without this it opened the app and did nothing.
        if (intent?.getBooleanExtra(VittWidget.EXTRA_ADD, false) == true) services.requestAdd()

        if (BuildConfig.DEBUG) {
            if (intent?.getBooleanExtra("seed", false) == true) services.seedSampleData()
            if (intent?.getBooleanExtra("stress", false) == true) services.seedStressData()
        }

        setContent {
            // Edge to edge, like iOS: the shared UI applies the status-bar and
            // navigation-bar insets itself (VittApp), so nothing is padded here.
            // Padding here as well would inset Android twice.
            //
            // The live Google checks, as VITT_VERIFY and VITT_AUTORUN are on
            // iOS, and gated the same way: a release build has no route to
            // them at all.
            //
            //   adb shell am start -n ie.shoonya.vitt/.MainActivity --ez autorun true
            //
            // Results go to logcat as VITT-CHECK lines.
            AppRoot(
                services,
                verify = BuildConfig.DEBUG && intent?.getBooleanExtra("verify", false) == true,
                autoRun = BuildConfig.DEBUG && intent?.getBooleanExtra("autorun", false) == true,
            )
        }
    }
}
