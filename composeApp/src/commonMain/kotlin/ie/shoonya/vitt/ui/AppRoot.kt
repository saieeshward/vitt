package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.VittServices
import ie.shoonya.vitt.auth.Crypto
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ie.shoonya.vitt.model.Choice
import ie.shoonya.vitt.ui.theme.AccentChoice
import ie.shoonya.vitt.ui.theme.ThemeChoice
import ie.shoonya.vitt.ui.theme.Vitt
import ie.shoonya.vitt.ui.theme.VittTheme

@Composable
fun AppRoot(
    services: VittServices,
    /** Runs the live Google checks instead of the app. Set from a launch variable. */
    verify: Boolean = false,
    autoRun: Boolean = false,
) {
    // The palette is read here rather than inside the app, because VittTheme
    // wraps everything below it: a theme the user picks two levels down has to
    // re-enter composition from above to take effect at all.
    var appearance by remember { mutableStateOf(0) }
    val theme = remember(appearance) {
        ThemeChoice.ofCode(services.ledger.choice(Choice.THEME))
    }
    val accent = remember(appearance) {
        AccentChoice.ofCode(services.ledger.choice(Choice.ACCENT))
    }

    VittTheme(theme = theme, accent = accent) {
        Surface(modifier = Modifier.fillMaxSize(), color = Vitt.colors.ground) {
            if (verify || autoRun) {
                VerifyScreen(services, autoRun = autoRun)
            } else {
                VittApp(
                    repository = services.ledger,
                    today = services.today(),
                    newId = { newTransactionId(services.today()) },
                    onAppearanceChange = { appearance++ },
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
