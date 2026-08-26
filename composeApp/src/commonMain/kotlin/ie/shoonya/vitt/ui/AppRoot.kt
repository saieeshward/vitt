package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.VittServices
import ie.shoonya.vitt.auth.Crypto
import ie.shoonya.vitt.ui.theme.Vitt
import ie.shoonya.vitt.ui.theme.VittTheme

@Composable
fun AppRoot(
    services: VittServices,
    /** Runs the live Google checks instead of the app. Set from a launch variable. */
    verify: Boolean = false,
    autoRun: Boolean = false,
) {
    VittTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Vitt.colors.ground) {
            if (verify || autoRun) {
                VerifyScreen(services, autoRun = autoRun)
            } else {
                VittApp(
                    repository = services.ledger,
                    today = services.today(),
                    newId = { newTransactionId(services.today()) },
                )
            }
        }
    }
}

/**
 * A time-ordered identifier.
 *
 * Random ids scatter across an index; a time-prefixed one keeps recent rows
 * together, which is what almost every query wants.
 */
private fun newTransactionId(day: Int): String {
    val random = Crypto.randomBytes(8).joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
    return day.toString(16).padStart(6, '0') + "-" + random
}
