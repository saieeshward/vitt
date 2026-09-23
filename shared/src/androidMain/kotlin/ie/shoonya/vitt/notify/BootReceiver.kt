package ie.shoonya.vitt.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Puts the daily reminder back after a restart.
 *
 * Android drops every alarm on reboot. Without this the reminder simply stops,
 * silently, and the person discovers weeks later that the app quietly gave up
 * on them — which is worse than never having offered.
 *
 * Reads the time from preferences rather than the ledger: this runs before the
 * app exists, and opening the database from a boot broadcast for the sake of
 * one string is not a trade worth making.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        initReminders(context)
        storedReminder(context)?.let { Reminders.apply(it) }
    }
}
