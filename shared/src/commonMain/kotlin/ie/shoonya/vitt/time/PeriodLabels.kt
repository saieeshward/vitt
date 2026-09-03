package ie.shoonya.vitt.time

/**
 * Naming periods for people.
 *
 * Pure formatting over [Period], and in `shared` rather than the UI module so it
 * can be tested: the branching is subtle — relative versus explicit wording, when
 * the year is worth saying, a week that straddles two months — and two bugs in it
 * were found by eye rather than by a test.
 *
 * Not localised. Like [ie.shoonya.vitt.money.Money.toPlainString] this is the
 * plain form; a localised one would need a platform formatter, and month
 * boundaries in this app deliberately do not come from the device.
 */
/**
 * What the period is called, which is how the grain gets communicated.
 *
 * Relative words for the one you are standing in and explicit dates for the
 * rest — the way people actually refer to periods, and the reason the word
 * "Month" never has to appear on screen.
 */
fun periodLabel(period: Period?, today: Int): String = when (period) {
    null -> "All time"
    is Day -> when (period.firstDay) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> {
            val (y, m, d) = Civil.fromDays(period.firstDay)
            val thisYear = Civil.fromDays(today).first
            "$d ${shortMonth(m)}" + if (y != thisYear) " $y" else ""
        }
    }
    is Week ->
        if (today in period) {
            "This week"
        } else {
            val (y1, m1, d1) = Civil.fromDays(period.firstDay)
            val (y2, m2, d2) = Civil.fromDays(period.lastDay)
            val thisYear = Civil.fromDays(today).first
            // "25–31 Aug" when one month, "28 Sep – 4 Oct" when it straddles.
            val left = if (m1 == m2) "$d1" else "$d1 ${shortMonth(m1)}"
            val right = "$d2 ${shortMonth(m2)}"
            "$left–$right" + if (y2 != thisYear) " $y2" else ""
        }
    is YearMonth -> monthLabel(period, Civil.fromDays(today).first)
    is Year -> period.year.toString()
}

/**
 * The period as a phrase that reads inside a sentence: "€40 out **this week**".
 *
 * Separate from [periodLabel] because a label and a phrase are not the same
 * grammar. Templating "out in ${'$'}{label.lowercase()}" produced "out in this
 * week" — relative periods take no preposition, explicit ones do.
 */
fun periodPhrase(period: Period?, today: Int): String = when (period) {
    null -> "in total"
    is Day -> when (period.firstDay) {
        today -> "today"
        today - 1 -> "yesterday"
        else -> "on " + periodLabel(period, today)
    }
    is Week -> if (today in period) "this week" else "in " + periodLabel(period, today)
    is YearMonth ->
        if (today in period) "this month" else "in " + periodLabel(period, today)
    is Year ->
        if (today in period) "this year" else "in " + periodLabel(period, today)
}

private fun shortMonth(month: Int) = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)[month - 1]

/**
 * A month, named for a person.
 *
 * The year is dropped for the current year: "September" is how someone refers to
 * a month they are standing in, and "September 2026" reads like a database
 * export.
 */
fun monthLabel(month: YearMonth, currentYear: Int): String {
    val name = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[month.month - 1]
    return if (month.year == currentYear) name else "$name ${month.year}"
}
