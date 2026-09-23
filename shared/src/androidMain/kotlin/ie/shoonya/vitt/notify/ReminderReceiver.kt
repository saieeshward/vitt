package ie.shoonya.vitt.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Posts the daily reminder when the alarm fires.
 *
 * Deliberately knows nothing about the ledger. It does not check whether
 * anything was recorded today and it does not vary its wording: reading the
 * database from a broadcast means opening it on a background thread at an
 * arbitrary moment, and the reminder is worth far less than the risk of
 * corrupting the one copy of someone's data. It asks the same question every
 * day and the app answers it.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // Tapping opens the app, nothing more. A notification action that
        // recorded a spend without showing it would be the one thing
        // AmountParser is written to avoid: an entry nobody confirmed.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = open?.let {
            android.app.PendingIntent.getActivity(
                context,
                0,
                it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = android.app.Notification.Builder(context, REMINDER_CHANNEL)
            .setContentTitle("Anything today?")
            .setContentText("A few seconds. Nothing to report is an answer too.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setAutoCancel(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        /** Fixed, so today's reminder replaces yesterday's rather than stacking. */
        const val NOTIFICATION_ID = 4201
    }
}
