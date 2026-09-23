package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable

// Stand-ins for the JVM target, which exists only so the UI can be tested
// without a device. Each one does nothing, visibly: a test that needs a file
// picked or a link opened is testing the platform, not this module.

@Composable
actual fun rememberFilePicker(): FilePicker = FilePicker { onPicked -> onPicked(null) }

@Composable
actual fun rememberFileExporter(): FileExporter = FileExporter { }

@Composable
actual fun rememberLinkOpener(): (String) -> Unit = { }

/** Motion stays on, so a test sees the app as most people do. */
@Composable
actual fun prefersReducedMotion(): Boolean = false
