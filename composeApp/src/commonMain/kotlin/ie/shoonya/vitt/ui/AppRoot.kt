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
fun AppRoot(services: VittServices, autoRun: Boolean = false) {
    VittTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Vitt.colors.ground) {
            VerifyScreen(services, autoRun = autoRun)
        }
    }
}
