package ie.shoonya.vitt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ie.shoonya.vitt.ui.SpikeApp

/**
 * The Android entry point. Mirrors iOS's `MainViewController`: it hosts the
 * shared Compose UI and owns nothing else.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SpikeApp() }
    }
}
