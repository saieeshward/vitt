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
    if (platform.Foundation.NSProcessInfo.processInfo.environment["VITT_SEED"] == "1") {
        services.seedSampleData()
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
