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
/**
 * The scheduling side, as an interface so the decision logic can be tested.
 *
 * Separate from [Reminders] rather than implemented by it: an `expect object`
 * carrying a supertype forces every actual to repeat it, which is friction for
 * no gain when a three-line adapter gives the same seam.
 */
interface ReminderScheduler {
    suspend fun requestPermission(): Boolean
    fun apply(reminder: Reminder?)
}

/** The real scheduler, wrapped for the call sites that want the interface. */
val platformReminderScheduler: ReminderScheduler = object : ReminderScheduler {
    override suspend fun requestPermission(): Boolean = Reminders.requestPermission()
    override fun apply(reminder: Reminder?) = Reminders.apply(reminder)
}

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

/**
 * Switching the reminder on or off, as one testable step.
 *
 * Extracted out of the settings composable because the interesting behaviour is
 * all in the branches, and a branch inside a lambda inside a bottom sheet is a
 * branch nothing can reach: the refusal path in particular had no coverage and
 * no way to get any.
 *
 * @return true when the choice was stored, false when the user declined.
 */
suspend fun applyReminderChoice(
    picked: Reminder?,
    scheduler: ReminderScheduler,
    setChoice: (String) -> Unit,
    clearChoice: () -> Unit,
): Boolean {
    // Permission only when switching it on. Asking to turn something off is
    // asking for nothing, and asking at launch is asking before the person
    // knows what for — a refusal is permanent, so the prompt is spent
    // carefully.
    if (picked != null && !scheduler.requestPermission()) {
        // Declined. The choice is left exactly as it was rather than stored
        // hopefully, so the picker keeps showing the truth instead of a time
        // that will never fire.
        return false
    }
    // Cleared, not stored blank: Choice.events refuses an empty value, and
    // absent is what "off" means everywhere else this is read.
    if (picked == null) clearChoice() else setChoice(picked.format())
    scheduler.apply(picked)
    return true
}
