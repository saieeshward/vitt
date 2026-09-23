package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable

/**
 * Opens an https URL in the system browser.
 *
 * Composable because Android needs a Context from composition. Only ever fed
 * constants from `Links`, never user text, so there is nothing to sanitise.
 */
@Composable
expect fun rememberLinkOpener(): (String) -> Unit
