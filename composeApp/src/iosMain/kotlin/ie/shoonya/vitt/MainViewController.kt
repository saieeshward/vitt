package ie.shoonya.vitt

import androidx.compose.ui.window.ComposeUIViewController
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.auth.TokenStore
import ie.shoonya.vitt.ui.AppRoot
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Entry point the Swift side hosts. Returns a plain UIViewController. */
fun MainViewController() = ComposeUIViewController {
    val services = VittServices(
        tokenStore = ie.shoonya.vitt.auth.platformTokenStore(),
        browser = BrowserAuth(),
        now = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
        driver = ie.shoonya.vitt.sync.iosDriver(),
    )
    val launch = platform.Foundation.NSProcessInfo.processInfo.environment
    if (launch["VITT_SEED"] == "1") {
        services.seedSampleData()
    }
    // The volume run, for finding what only breaks at scale. Separate variable
    // rather than a bigger VITT_SEED, because the ordinary sample is what the
    // screenshots and the day-to-day checks are read against.
    if (launch["VITT_STRESS"] == "1") {
        services.seedStressData()
    }
    AppRootWith(services)
}



/** Reads the launch variables that select the app or the verification harness. */
@androidx.compose.runtime.Composable
private fun AppRootWith(services: VittServices) {
    val env = platform.Foundation.NSProcessInfo.processInfo.environment
    AppRoot(
        services,
        verify = env["VITT_VERIFY"] == "1",
        autoRun = env["VITT_AUTORUN"] == "1",
    )
}
