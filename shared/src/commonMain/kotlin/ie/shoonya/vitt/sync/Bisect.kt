package ie.shoonya.vitt.sync

/**
 * Finds which events in a rejected batch are actually to blame.
 *
 * Sheets' append is all-or-nothing, so one malformed row — an over-long string,
 * a cell past the 50,000-character limit — fails the whole request with a 400.
 * Treating that as "the batch is bad" poisons up to 500 financial records
 * because of one of them, which is the opposite of the rule that nothing is ever
 * silently dropped.
 *
 * Binary search costs O(k log n) sends for k offenders rather than the n sends a
 * one-by-one retry would take, which matters because sends are rate-limited to
 * 60 per minute.
 */
object Bisect {

    /**
     * @param send attempts a sublist; returns true if it was accepted. Must be
     * safe to call repeatedly with the same items — the HLC idempotency key is
     * what makes that true here.
     * @return the items that fail on their own. Empty if the whole batch
     * succeeds on retry, which happens when the original failure was transient.
     */
    suspend fun <T> failing(items: List<T>, send: suspend (List<T>) -> Boolean): List<T> {
        if (items.isEmpty()) return emptyList()
        if (send(items)) return emptyList()
        if (items.size == 1) return items

        val mid = items.size / 2
        val left = failing(items.take(mid), send)
        val right = failing(items.drop(mid), send)
        return left + right
    }
}
