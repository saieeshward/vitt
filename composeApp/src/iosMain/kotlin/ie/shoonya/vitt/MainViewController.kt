package ie.shoonya.vitt

import androidx.compose.ui.window.ComposeUIViewController
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.auth.TokenStore
import ie.shoonya.vitt.ui.AppRoot
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * True only in a debug binary.
 *
 * The seeders and the verification harness are development tools reached by
 * launch variable. A release build that still honours those variables is a
 * hidden feature under Apple 2.5.1, so the gate is the build kind rather than
 * the variable: in a store binary the switches do not exist at all.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private val isDebugBuild: Boolean get() = kotlin.native.Platform.isDebugBinary

/** Entry point the Swift side hosts. Returns a plain UIViewController. */
fun MainViewController() = ComposeUIViewController {
    val services = VittServices(
        tokenStore = ie.shoonya.vitt.auth.platformTokenStore(),
        browser = BrowserAuth(),
        now = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
        driver = ie.shoonya.vitt.sync.iosDriver(),
    )
    val launch =
        if (isDebugBuild) platform.Foundation.NSProcessInfo.processInfo.environment
        else emptyMap<Any?, Any?>()
    if (launch["VITT_SEED"] == "1") {
        services.seedSampleData()
    }
    // The volume run, for finding what only breaks at scale. Separate variable
    // rather than a bigger VITT_SEED, because the ordinary sample is what the
    // screenshots and the day-to-day checks are read against.
    if (launch["VITT_STRESS"] == "1") {
        services.seedStressData()
    }
    // Text from a Shortcut or the share extension. Setting this replays
    // anything that arrived during a cold launch, before this line ran.
    IosCapture.onText = { services.onSharedText(it) }

    // Returning from the background is the moment another device's entries
    // are most likely waiting. The launch itself syncs from AppRoot.
    platform.Foundation.NSNotificationCenter.defaultCenter.addObserverForName(
        name = platform.UIKit.UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = null,
    ) { services.sync.onForeground() }
    AppRootWith(services)
}



/** Reads the launch variables that select the app or the verification harness. */
@androidx.compose.runtime.Composable
private fun AppRootWith(services: VittServices) {
    // Same gate as the seeders: a release binary reaches the app and nothing
    // else, so VerifyScreen has no reachable entry point in a store build.
    val env =
        if (isDebugBuild) platform.Foundation.NSProcessInfo.processInfo.environment
        else emptyMap<Any?, Any?>()
    AppRoot(
        services,
        verify = env["VITT_VERIFY"] == "1",
        autoRun = env["VITT_AUTORUN"] == "1",
    )
}
