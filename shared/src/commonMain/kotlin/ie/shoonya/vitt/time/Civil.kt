package ie.shoonya.vitt.time

/**
 * Calendar arithmetic on days since the Unix epoch, in integers only.
 *
 * A leaf with no dependencies, so every layer can use it. The algorithms are
 * Howard Hinnant's `civil_from_days` and `days_from_civil` — proleptic Gregorian,
 * exact for any year, and no floating point or platform date API involved. That
 * matters twice over here: `kotlinx-datetime` would be another dependency for
 * arithmetic this small, and a platform calendar would put the app's month
 * boundaries at the mercy of a device locale.
 *
 * Everything is UTC. A day in this app is a date with no time and no zone
 * (see [ie.shoonya.vitt.model.Transaction.day]), so there is no zone to apply.
 */
object Civil {

    /** Splits days-since-epoch into a proleptic Gregorian year, month and day. */
    fun fromDays(daysSinceEpoch: Int): Triple<Int, Int, Int> {
        val z = daysSinceEpoch + 719_468L
        val era = (if (z >= 0) z else z - 146_096L) / 146_097L
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    /** Days since the epoch for a year, month and day. The inverse of [fromDays]. */
    fun toDays(year: Int, month: Int, day: Int): Int {
        val yAdj = if (month <= 2) year - 1 else year
        val era = (if (yAdj >= 0) yAdj else yAdj - 399) / 400
        val yoe = yAdj - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return (era * 146_097L + doe - 719_468L).toInt()
    }

    /** Days in a month, honouring the full leap-year rule. */
    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        // Not `year % 4 == 0`: 1900 was not a leap year and 2000 was.
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> throw IllegalArgumentException("no month $month")
    }
}

/**
 * A calendar month, which is the period a budget is set against.
 *
 * A month rather than a rolling 30 days because that is how rent, salaries and
 * standing orders land, and a budget that resets on a different day each month
 * cannot be reconciled against a bank statement.
 */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init {
        require(month in 1..12) { "no month $month" }
    }

    val firstDay: Int get() = Civil.toDays(year, month, 1)

    val lastDay: Int get() = Civil.toDays(year, month, Civil.daysInMonth(year, month))

    operator fun contains(day: Int): Boolean = day in firstDay..lastDay

    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)

    fun previous(): YearMonth =
        if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)

    override fun compareTo(other: YearMonth): Int =
        if (year != other.year) year - other.year else month - other.month

    /** `2026-09`. Machine-plain, not localised — the UI layer names months. */
    override fun toString(): String = "$year-" + month.toString().padStart(2, '0')

    companion object {
        /** The month a given day falls in. */
        fun of(day: Int): YearMonth {
            val (y, m, _) = Civil.fromDays(day)
            return YearMonth(y, m)
        }
    }
}
