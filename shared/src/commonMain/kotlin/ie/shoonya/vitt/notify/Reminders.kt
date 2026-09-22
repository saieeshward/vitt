package ie.shoonya.vitt.notify

/**
 * Schedules or cancels the one daily reminder.
 *
 * Local only. There is no server to push from and no account to push to, and
 * this has to keep working on a phone in aeroplane mode. Exactly one pending
 * notification exists at a time: applying a new time replaces the old one
 * rather than adding to it, so a person who changes the time twice does not end
 * up with three reminders.
 */
expect object Reminders {

    /**
     * Asks the OS for permission, returning whether it was granted.
     *
     * Called only when the user turns the reminder on, never at launch. A
     * permission prompt on first open is asking for something before the person
     * knows what it is for, and a refusal is permanent.
     */
    suspend fun requestPermission(): Boolean

    /** Applies a time, or cancels when null. */
    fun apply(reminder: Reminder?)
}
