package ie.shoonya.vitt

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ie.shoonya.vitt.widget.WIDGET_KEY
import ie.shoonya.vitt.widget.WIDGET_PREFS
import ie.shoonya.vitt.widget.WIDGET_UPDATED
import ie.shoonya.vitt.widget.WidgetSnapshot
import ie.shoonya.vitt.widget.display

/**
 * The home screen widget: this month's figure, and a button that logs.
 *
 * It reads a snapshot the app publishes, never the database. Opening SQLite
 * from a broadcast means touching the one copy of someone's data at an
 * arbitrary moment for the sake of a number, and the number is not worth it.
 * The snapshot is four fields in shared preferences.
 *
 * The button is the reason the widget exists. A manual tracker does not fail
 * because people stop caring, it fails because logging costs more than the
 * moment is worth: unlock, find the app, open it, tap, type. One tap from the
 * home screen is a different product.
 */
class VittWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // The app telling us the figures moved.
        if (intent.action == WIDGET_UPDATED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VittWidget::class.java))
            onUpdate(context, manager, ids)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = WidgetSnapshot.decode(
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getString(WIDGET_KEY, null),
        )

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_vitt)

            if (snapshot == null) {
                // Nothing recorded yet, or a payload this build cannot read.
                // Says so plainly rather than showing a confident zero, which
                // would look like a real figure.
                views.setTextViewText(R.id.widget_label, "VITT")
                views.setTextViewText(R.id.widget_amount, "—")
                views.setTextViewText(R.id.widget_note, "Nothing recorded yet")
            } else {
                views.setTextViewText(R.id.widget_label, "${snapshot.currencyCode} this month")
                views.setTextViewText(R.id.widget_amount, snapshot.display())
                views.setTextViewText(R.id.widget_note, note(snapshot))
            }

            // Both the button and the body open the app. A widget that only
            // responds on one small target feels broken.
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            open?.let {
                it.putExtra(EXTRA_ADD, true)
                val pending = PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_add, pending)
            }

            manager.updateAppWidget(id, views)
        }
    }

    /**
     * The quiet line under the figure.
     *
     * Never a scolding. §5's Gentle tier is explicit that the layer may not
     * make someone feel worse for spending, so being over budget is stated as
     * a fact and nothing else.
     */
    private fun note(snapshot: WidgetSnapshot): String {
        val pressure = snapshot.pressure()
        return when {
            pressure == null && snapshot.recordedDays > 0 ->
                "${snapshot.recordedDays} days recorded"
            pressure == null -> ""
            pressure > 1f -> "Over the budget"
            else -> "${((1f - pressure) * 100).toInt()}% of the budget left"
        }
    }

    companion object {
        /** Tells the app it was opened by the widget's button, so it opens the sheet. */
        const val EXTRA_ADD = "vitt.widget.add"
    }
}
