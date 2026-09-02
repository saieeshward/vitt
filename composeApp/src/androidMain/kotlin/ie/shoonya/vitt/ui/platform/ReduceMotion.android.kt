package ie.shoonya.vitt.ui.platform

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android has no single "reduce motion" flag.
 *
 * Developer options and the accessibility "remove animations" setting both drive
 * `ANIMATOR_DURATION_SCALE` to zero, which is the signal the platform's own
 * animators respect, so it is the honest thing to read.
 */
@Composable
actual fun prefersReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}
