package ie.shoonya.vitt.notify

/**
 * The daily "anything today?" reminder, as a stored value.
 *
 * A manual tracker does not die of boredom, it dies of friction debt: miss three
 * days and opening the app stops being a tool and becomes homework you are
 * behind on. The reminder exists to keep the gap from opening, which is why the
 * answer it invites is one tap and why "nothing today" is a real answer:
 * `LedgerRepository.noSpendDays` already counts a day you spent nothing as a day
 * you recorded, so a streak is broken by silence rather than by living cheaply.
 *
 * Local, never pushed. There is no server to push from and no account to push
 * to, and this must keep working on a phone in aeroplane mode. It is also
 * emphatically not notification *reading*, which `PLAN.md` §0 rules out as a
 * Play Sensitive Permissions violation: the app posts one of its own and reads
 * nothing.
 *
 * Off unless asked for, per §5's Gentle tier. One a day, at a time the person
 * picked, with no second nag when it is ignored.
 */
data class Reminder(val minutesSinceMidnight: Int) {

    val hour: Int get() = minutesSinceMidnight / 60
    val minute: Int get() = minutesSinceMidnight % 60

    /** 24-hour, zero padded. The stored form and the displayed one are the same. */
    fun format(): String {
        val h = hour.toString().padStart(2, '0')
        val m = minute.toString().padStart(2, '0')
        return "$h:$m"
    }

    companion object {
        /**
         * Early evening: after the day's spending has happened and before the
         * phone is put down for the night. A morning reminder asks about a day
         * that has not happened yet.
         */
        val DEFAULT = Reminder(21 * 60)

        const val MINUTES_IN_DAY = 24 * 60

        /**
         * Parses a stored `HH:mm`, or null when it is absent or unreadable.
         *
         * Null means off, and an unparseable value means off rather than a
         * default: a row this build cannot read may come from a newer one, and
         * quietly substituting a different time would fire a notification the
         * person never asked for at an hour they did not choose.
         */
        fun ofCode(code: String?): Reminder? {
            val parts = code?.trim()?.split(':') ?: return null
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return Reminder(h * 60 + m)
        }

        /** The times offered in Settings. Fine-grained enough to matter, short enough to scan. */
        val CHOICES: List<Reminder> = listOf(8, 12, 18, 20, 21, 22).map { Reminder(it * 60) }
    }
}
