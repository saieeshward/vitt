package ie.shoonya.tracker.sync

/**
 * Fixed-width UTC timestamp formatting: exactly `2026-08-25T22:23:42.123Z`,
 * 24 characters, always.
 *
 * Hand-rolled rather than delegated to a date library for one reason: the width
 * must never vary. HLC comparison is lexicographic string comparison, so a
 * formatter that trims a trailing zero from the milliseconds — or renders a
 * year as 5 digits — silently breaks the ordering of every timestamp in the
 * user's spreadsheet. Pinning the format here makes that impossible.
 *
 * Uses Howard Hinnant's civil-from-days algorithm, which is exact for all
 * proleptic Gregorian dates and avoids any dependency on platform time zones.
 */
internal object Iso8601 {

    const val LENGTH = 24
    private const val MILLIS_PER_DAY = 86_400_000L

    fun format(epochMillis: Long): String {
        var days = epochMillis.floorDiv(MILLIS_PER_DAY)
        var millisOfDay = epochMillis.mod(MILLIS_PER_DAY)

        val (y, m, d) = civilFromDays(days)
        val millis = (millisOfDay % 1000).toInt()
        millisOfDay /= 1000
        val second = (millisOfDay % 60).toInt()
        val minute = ((millisOfDay / 60) % 60).toInt()
        val hour = (millisOfDay / 3600).toInt()

        return buildString(LENGTH) {
            append(pad(y, 4)); append('-')
            append(pad(m, 2)); append('-')
            append(pad(d, 2)); append('T')
            append(pad(hour, 2)); append(':')
            append(pad(minute, 2)); append(':')
            append(pad(second, 2)); append('.')
            append(pad(millis, 3)); append('Z')
        }
    }

    fun parse(iso: String): Long {
        require(iso.length == LENGTH && iso[10] == 'T' && iso[23] == 'Z') {
            "not a fixed-width UTC timestamp: '$iso'"
        }
        val year = iso.substring(0, 4).toInt()
        val month = iso.substring(5, 7).toInt()
        val day = iso.substring(8, 10).toInt()
        val hour = iso.substring(11, 13).toInt()
        val minute = iso.substring(14, 16).toInt()
        val second = iso.substring(17, 19).toInt()
        val millis = iso.substring(20, 23).toInt()
        val days = daysFromCivil(year, month, day)
        return days * MILLIS_PER_DAY +
            hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis
    }

    private fun pad(value: Int, width: Int): String {
        val s = value.toString()
        return if (s.length >= width) s else "0".repeat(width - s.length) + s
    }

    /** Days since 1970-01-01 for a civil date. Exact; no leap-year special cases missed. */
    private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
        val yAdj = if (m <= 2) y - 1 else y
        val era = (if (yAdj >= 0) yAdj else yAdj - 399) / 400
        val yoe = yAdj - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146_097L + doe.toLong() - 719_468L
    }

    private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
        val z = daysSinceEpoch + 719_468L
        val era = (if (z >= 0) z else z - 146_096L) / 146_097L
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }
}
