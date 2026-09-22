package ie.shoonya.vitt.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

private var remindersContext: Context? = null

/** Called once from the app shell, like `initTokenStore`. */
fun initReminders(context: Context) {
    remindersContext = context.applicationContext
}

/** The channel the daily reminder posts on. Created lazily, once. */
const val REMINDER_CHANNEL = "vitt.daily"

actual object Reminders {

    /**
     * A fixed request code, so an alarm replaces rather than accumulates.
     *
     * `FLAG_UPDATE_CURRENT` with the same code and intent rewrites the pending
     * intent instead of adding one, which is what stops a person who changes
     * the time twice from being reminded three times.
     */
    private const val REQUEST = 4201

    /**
     * Android asks at the OS level from 13; before that, posting was allowed
     * without a prompt. Returning true on older versions is not optimism, it is
     * the actual permission state.
     */
    actual suspend fun requestPermission(): Boolean {
        val context = remindersContext ?: return false
        ensureChannel(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    actual fun apply(reminder: Reminder?) {
        val context = remindersContext ?: return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (reminder == null) {
            alarms.cancel(intent)
            return
        }

        ensureChannel(context)
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Today's slot may already have passed, and an alarm in the past
            // fires immediately: turning the reminder on at 22:00 for a 21:00
            // slot would buzz straight away.
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        // Inexact on purpose. An exact alarm needs SCHEDULE_EXACT_ALARM, which
        // Play treats as a sensitive permission wanting justification, and a
        // reminder that lands within a few minutes of nine is a reminder that
        // did its job.
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            intent,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(REMINDER_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL,
                "Daily reminder",
                // Default, not high: this is an invitation, not an alert.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "One a day, at the time you chose."
                setShowBadge(false)
            },
        )
    }
}
