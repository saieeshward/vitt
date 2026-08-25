package ie.shoonya.tracker

import androidx.compose.ui.window.ComposeUIViewController
import ie.shoonya.tracker.ui.SpikeApp

/** Entry point the Swift side hosts. Returns a plain UIViewController. */
fun MainViewController() = ComposeUIViewController { SpikeApp() }
