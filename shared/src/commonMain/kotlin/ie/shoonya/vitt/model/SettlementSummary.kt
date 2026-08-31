package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money

/**
 * A plain-text account of what someone owes, ready for a mail composer.
 *
 * This exists because there is no other reliable way to tell a co-participant
 * anything. Push notifications need a server (FCM, APNs and Drive
 * `changes.watch` all do) and §0 forbids one; a shared sheet only updates on the
 * other person's next poll. An email the *user* sends is the one channel that
 * actually arrives — and it costs no OAuth scope, because the app only composes
 * the text and hands it to the platform. Asking for `gmail.send` would make the
 * scope set sensitive and trigger CASA, verification and the 100-user cap, which
 * §0.2 calls the highest-leverage decision in the project.
 *
 * Pure logic in `commonMain` so it is testable and identical on both platforms.
 * The platform layer supplies only the composer.
 */
object SettlementSummary {

    /**
     * Builds the subject and body for one participant.
     *
     * Totals stay per currency throughout, never blended. A single converted
     * figure would need a market rate, and a rate that moves after the fact means
     * the amount owed changes with the market — the failure §0.6 exists to
     * prevent, and the one Splitwise Pro has by design.
     *
     * Returns null when nothing is outstanding, so a caller cannot send an empty
     * demand for money.
     */
    fun forParticipant(
        participant: String,
        splits: List<Transaction>,
        senderName: String? = null,
        formatDay: (Int) -> String = { "day $it" },
    ): Message? {
        val theirs = splits
            .filter { it.isSplit && !it.isSettled && participant.lowercase() in it.splitWith }
            .sortedBy { it.day }
        if (theirs.isEmpty()) return null

        val totals = theirs
            .mapNotNull { t -> t.outstandingFor(participant)?.let { t.amount.currency to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (currency, amounts) ->
                amounts.fold(Money(0, currency)) { acc, m -> acc + m }
            }
            .filterValues { it.minor > 0 }
        if (totals.isEmpty()) return null

        val lines = mutableListOf<String>()
        lines += theirs.map { t ->
            val what = t.merchant ?: t.category ?: "expense"
            val paid = t.totalPaid?.abs()?.format() ?: t.amount.abs().format()
            // Their share of what is left, not the split's whole remainder —
            // otherwise two people each get billed for the same money.
            val theirShare = t.outstandingFor(participant)?.format()
            val between = if (t.splitWith.size > 1) " between ${t.splitWith.size}" else ""
            "${formatDay(t.day)} — $what, $paid paid$between, $theirShare owed"
        }
        lines += ""
        lines += if (totals.size == 1) {
            "Total owed: ${totals.values.single().format()}"
        } else {
            // Listed, not summed. Two facts beat one guess.
            "Total owed, per currency:" +
                totals.entries.sortedBy { it.key.code }
                    .joinToString("") { "\n  ${it.value.format()}" }
        }
        senderName?.let { lines += "\n— $it" }

        return Message(
            subject = subjectFor(totals),
            body = lines.joinToString("\n"),
        )
    }

    private fun subjectFor(totals: Map<Currency, Money>): String =
        if (totals.size == 1) {
            "Settling up — ${totals.values.single().format()}"
        } else {
            "Settling up — ${totals.size} currencies"
        }

    /**
     * Machine-plain rather than localised.
     *
     * The recipient's locale is unknown and unknowable, so a grouped, localised
     * figure could be read as a different number entirely — `1.234` is 1234 in
     * Germany and 1.234 in Ireland (`PLAN.md:460`). Code plus a plain decimal is
     * unambiguous everywhere.
     */
    private fun Money.format(): String = "${currency.code} ${toPlainString()}"

    data class Message(val subject: String, val body: String)
}
