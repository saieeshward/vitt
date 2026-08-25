package ie.shoonya.vitt

import androidx.compose.ui.window.ComposeUIViewController
import ie.shoonya.vitt.ui.SpikeApp

/** Entry point the Swift side hosts. Returns a plain UIViewController. */
fun MainViewController() = ComposeUIViewController { SpikeApp() }
