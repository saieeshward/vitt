package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * Read once per composition rather than observed.
 *
 * The setting changes in Settings.app, which backgrounds VITT, and the home
 * screen recomposes on return — so a notification observer would buy nothing for
 * the extra lifecycle to get wrong.
 */
@Composable
actual fun prefersReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
