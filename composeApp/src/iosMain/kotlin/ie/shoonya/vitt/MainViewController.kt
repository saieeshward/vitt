package ie.shoonya.vitt

import androidx.compose.ui.window.ComposeUIViewController
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.auth.TokenStore
import ie.shoonya.vitt.ui.AppRoot
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Entry point the Swift side hosts. Returns a plain UIViewController. */
fun MainViewController() = ComposeUIViewController {
    AppRoot(
        VittServices(
            tokenStore = ie.shoonya.vitt.auth.platformTokenStore(),
            browser = BrowserAuth(),
            now = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
            driver = ie.shoonya.vitt.sync.iosDriver(),
        ),
        // Both come from launch variables so the checks can be driven without a
        // human tapping a button; the app itself is the default.
        verify = platform.Foundation.NSProcessInfo.processInfo
            .environment["VITT_VERIFY"] == "1",
        autoRun = platform.Foundation.NSProcessInfo.processInfo
            .environment["VITT_AUTORUN"] == "1",
    )
}
