package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.VittServices

@Composable
fun AppRoot(services: VittServices) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            VerifyScreen(services)
        }
    }
}
