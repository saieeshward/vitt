package ie.shoonya.vitt.widget

import platform.Foundation.NSUserDefaults

/**
 * The App Group both the app and the widget can see.
 *
 * A widget extension has its own container, so the app's own defaults are
 * invisible to it. This group has to be declared as an entitlement on both
 * targets and registered against the team, which is why the widget cannot ship
 * before the developer account exists. It works in the Simulator meanwhile.
 */
const val APP_GROUP = "group.ie.shoonya.vitt"

/** Set from Swift, which owns WidgetKit. Kotlin/Native has no binding for it. */
var reloadWidgets: (() -> Unit)? = null

actual fun publishWidgetSnapshot(payload: String?) {
    val defaults = NSUserDefaults(suiteName = APP_GROUP)
    if (payload == null) {
        defaults.removeObjectForKey("snapshot")
    } else {
        defaults.setObject(payload, forKey = "snapshot")
    }
    // Without this the widget keeps its last timeline until iOS decides
    // otherwise, which can be hours.
    reloadWidgets?.invoke()
}
