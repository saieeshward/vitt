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
import ie.shoonya.vitt.widget.perDayDisplay

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
open class VittWidget : AppWidgetProvider() {

    /**
     * Which figure this provider leads with.
     *
     * Two widgets rather than one with a setting, because a widget's whole job
     * is to be glanceable and a person who wants the running total and a person
     * who wants the daily allowance are asking different questions. Letting
     * them place either, or both, is cheaper than a configuration screen.
     */
    protected open val showRoom: Boolean = true

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // The app telling us the figures moved. Resolved against this concrete
        // provider rather than the base class, or the spent widget would ask
        // the launcher for the room widget's ids and update nothing.
        if (intent.action == WIDGET_UPDATED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
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
                val today = (System.currentTimeMillis() / 86_400_000L).toInt()
                val perDay = if (showRoom) snapshot.perDayDisplay(today) else null
                if (perDay == null) {
                    views.setTextViewText(R.id.widget_label, "${snapshot.currencyCode} this month")
                    views.setTextViewText(R.id.widget_amount, snapshot.display())
                    views.setTextViewText(R.id.widget_note, note(snapshot))
                } else {
                    // The per-day figure leads, because it is the only number
                    // here anyone can act on. It rises on a day nothing was
                    // spent, and it does so on its own at midnight, which is
                    // the entire reason to glance at a widget twice.
                    val days = snapshot.daysLeft(today)
                    views.setTextViewText(R.id.widget_label, "Room left")
                    views.setTextViewText(R.id.widget_amount, perDay)
                    views.setTextViewText(
                        R.id.widget_note,
                        if (days == 1) "for the last day" else "a day for $days days",
                    )
                }
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

        scheduleMidnight(context)
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

        private const val MIDNIGHT_REQUEST = 4202
    }

    /**
     * Wakes the widget at the next midnight.
     *
     * Android has no timeline: a RemoteViews update only happens when
     * something asks for it. Without this the per-day figure would sit at
     * yesterday's value until the app was next opened, which would break the
     * one property that makes it worth looking at.
     *
     * Inexact, for the reason the reminder is: an exact alarm needs a
     * permission Play wants justified, and a figure that refreshes a few
     * minutes after midnight is correct for every practical purpose.
     */
    private fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val next = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 1)
            set(java.util.Calendar.SECOND, 0)
        }
        alarms.setInexactRepeating(
            android.app.AlarmManager.RTC,
            next.timeInMillis,
            android.app.AlarmManager.INTERVAL_DAY,
            PendingIntent.getBroadcast(
                context,
                MIDNIGHT_REQUEST,
                Intent(WIDGET_UPDATED).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
}

/**
 * The same snapshot, led by the running total instead.
 *
 * For anyone who wants the plain fact of what they have spent rather than the
 * allowance. Both can sit on the home screen at once.
 */
class VittSpentWidget : VittWidget() {
    override val showRoom: Boolean = false
}
