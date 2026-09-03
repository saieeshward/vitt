package ie.shoonya.vitt.time

/**
 * A stretch of days the app can be looking at.
 *
 * One shape for every grain, so the UI is a label and two arrows whatever the
 * user picked — the whole reason period selection can stay one line instead of a
 * date-range picker.
 *
 * All-time is deliberately *not* a member here. It is the absence of a period,
 * represented as `null`, because a Period with no bounds would force every
 * `firstDay`/`lastDay` caller to special-case it.
 */
sealed interface Period {
    /** First day in the period, as days since the Unix epoch. */
    val firstDay: Int

    /** Last day in the period, inclusive. */
    val lastDay: Int

    fun next(): Period
    fun previous(): Period

    /** Default for every grain: the bounds are all a period is. */
    operator fun contains(day: Int): Boolean = day in firstDay..lastDay

    /** The grain, for a UI that needs to know which arrows to draw. */
    val grain: Grain

    enum class Grain { DAY, WEEK, MONTH, YEAR }
}

/** A single day. */
data class Day(override val firstDay: Int) : Period {
    override val lastDay: Int get() = firstDay
    override fun next(): Day = Day(firstDay + 1)
    override fun previous(): Day = Day(firstDay - 1)
    override val grain: Period.Grain get() = Period.Grain.DAY
}

/**
 * A seven-day week starting on Monday.
 *
 * Monday, fixed, and not read from the device. Apple's Fitness app does the same
 * — its week starts on Monday regardless of locale or the Calendar app's own
 * setting — and it matches ISO-8601. It is also consistent with how this app
 * treats every other boundary: [Civil] exists precisely so that a device setting
 * cannot move the month, and letting one move the week would be the same mistake
 * in a smaller place.
 */
data class Week(override val firstDay: Int) : Period {
    override val lastDay: Int get() = firstDay + 6
    override fun next(): Week = Week(firstDay + 7)
    override fun previous(): Week = Week(firstDay - 7)
    override val grain: Period.Grain get() = Period.Grain.WEEK

    companion object {
        /** The Monday-based week containing [day]. */
        fun containing(day: Int): Week = Week(day - Civil.mondayOffset(day))
    }
}

/** A calendar year. */
data class Year(val year: Int) : Period {
    override val firstDay: Int get() = Civil.toDays(year, 1, 1)
    override val lastDay: Int get() = Civil.toDays(year, 12, 31)
    override fun next(): Year = Year(year + 1)
    override fun previous(): Year = Year(year - 1)
    override val grain: Period.Grain get() = Period.Grain.YEAR
}
