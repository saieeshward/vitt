package ie.shoonya.vitt.widget

import android.content.Context
import android.content.Intent

private var widgetContext: Context? = null

/** Called once from the app shell, like `initTokenStore`. */
fun initWidgets(context: Context) {
    widgetContext = context.applicationContext
}

/** Where the widget reads from. Plain preferences: it is four fields, not a database. */
const val WIDGET_PREFS = "vitt.widget"
const val WIDGET_KEY = "snapshot"

/** Broadcast to the provider, so an update is a push rather than a poll. */
const val WIDGET_UPDATED = "ie.shoonya.vitt.WIDGET_UPDATED"

actual fun publishWidgetSnapshot(payload: String?) {
    val context = widgetContext ?: return
    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .edit()
        .apply { if (payload == null) remove(WIDGET_KEY) else putString(WIDGET_KEY, payload) }
        .apply()

    // Explicit package, because an implicit broadcast would not be delivered
    // to a manifest-registered receiver on modern Android.
    context.sendBroadcast(Intent(WIDGET_UPDATED).setPackage(context.packageName))
}
