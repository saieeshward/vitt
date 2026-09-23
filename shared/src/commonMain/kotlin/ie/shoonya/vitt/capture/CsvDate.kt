package ie.shoonya.vitt.capture

import ie.shoonya.vitt.time.Civil

/**
 * Turning a bank export's dates into days, without guessing.
 *
 * `03/04/2026` is the third of April in Dublin and the fourth of March in New
 * York, and nothing in the row says which. Guessing wrong does not fail — it
 * silently misdates every transaction in the file, moves them between months,
 * and quietly corrupts every budget and report that follows. `LiveVerification`
 * already names this as the locale trap for the sheet; it is worse here,
 * because a CSV can carry a year of history in one go.
 *
 * So the order is decided **from the whole file at once**, never per row. One
 * unambiguous row — any date with a component above twelve — settles the order
 * for every other row, because a bank does not change format halfway down its
 * own export. When no row settles it, [order] returns [Order.AMBIGUOUS] and the
 * caller has to ask. That is the honest answer, and it matches the rest of
 * capture: `AmountParser` would rather be unsure than guess a sign, for exactly
 * the same reason.
 */
object CsvDate {

    enum class Order {
        /** 03/04 is the 3rd of April. Ireland, India, most of the world. */
        DAY_FIRST,

        /** 03/04 is the 4th of March. The United States. */
        MONTH_FIRST,

        /** Nothing in the file settles it, so the user has to say. */
        AMBIGUOUS,

        /** Every date is ISO or otherwise self-describing; order does not apply. */
        UNAMBIGUOUS,
    }

    /**
     * ISO first, because `2026-04-03` can only mean one thing and many banks
     * now export it.
     */
    private val iso = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")

    /** Two numbers then a year: the ambiguous shape this class exists for. */
    // Four digits before two, because regex alternation is ordered: with the
    // two-digit branch first, "2026" matched as "20" and every date in the file
    // silently moved to 2020.
    private val pair = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})""")

    /**
     * Decides the order for a whole file.
     *
     * Looks for a single row that cannot be read both ways. One is enough:
     * `13/04/2026` has no thirteenth month, so the file is day-first and every
     * other row follows.
     */
    fun order(raws: List<String?>): Order {
        var sawPair = false
        raws.forEach { raw ->
            val m = pair.find(raw?.trim().orEmpty()) ?: return@forEach
            sawPair = true
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (a > 12 && b <= 12) return Order.DAY_FIRST
            if (b > 12 && a <= 12) return Order.MONTH_FIRST
        }
        return if (sawPair) Order.AMBIGUOUS else Order.UNAMBIGUOUS
    }

    /**
     * Days since the epoch, or null when the text is not a date this understands.
     *
     * [order] only matters for the ambiguous shape; an ISO date ignores it.
     * Null rather than a fallback: a row whose date cannot be read is a row for
     * the review pile, not one to file under today.
     */
    fun parse(raw: String?, order: Order): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        iso.find(text)?.let { m ->
            return day(
                year = m.groupValues[1].toInt(),
                month = m.groupValues[2].toInt(),
                dayOfMonth = m.groupValues[3].toInt(),
            )
        }

        val m = pair.find(text) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val year = m.groupValues[3].let {
            // Two digits are this century. A bank export of 1926 does not exist,
            // and the alternative is a sliding window that changes meaning as
            // the years pass.
            if (it.length == 2) 2000 + it.toInt() else it.toInt()
        }

        // A row that settles itself beats the file-wide order, so a stray
        // ISO-ish or unambiguous row is still read correctly.
        val (dayOfMonth, month) = when {
            a > 12 && b <= 12 -> a to b
            b > 12 && a <= 12 -> b to a
            order == Order.MONTH_FIRST -> b to a
            order == Order.DAY_FIRST -> a to b
            // Ambiguous and unresolved: refuse rather than pick.
            else -> return null
        }
        return day(year, month, dayOfMonth)
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int): Int? {
        if (month !in 1..12) return null
        if (dayOfMonth !in 1..Civil.daysInMonth(year, month)) return null
        return Civil.toDays(year, month, dayOfMonth)
    }
}
