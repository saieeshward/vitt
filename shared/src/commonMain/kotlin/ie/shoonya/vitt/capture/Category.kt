package ie.shoonya.vitt.capture

/**
 * The category taxonomy, locked by `PLAN.md` §6.
 *
 * Fourteen, currency-agnostic, and assigned **by purpose rather than by
 * merchant** — a work coffee and a personal coffee are both Dining, and a
 * supermarket that sold you a kettle is still Groceries unless you say otherwise.
 *
 * Locked means locked: the set is a stated decision, not a starting point.
 * Splitting or renaming one later re-categorises history, because rules and
 * transactions both refer to it by [code].
 *
 * The codes are written out rather than derived from [name] so that renaming a
 * constant cannot silently orphan every row in the user's sheet.
 */
enum class Category(val code: String, val label: String) {
    GROCERIES("groceries", "Groceries"),
    DINING("dining", "Dining"),
    TRANSPORT("transport", "Transport"),
    HOUSING("housing", "Housing"),
    UTILITIES("utilities", "Utilities"),
    HEALTH("health", "Health"),
    SHOPPING("shopping", "Shopping"),
    ENTERTAINMENT("entertainment", "Entertainment"),
    TRAVEL("travel", "Travel"),
    FAMILY_GIFTS("family_gifts", "Family & Gifts"),
    SUBSCRIPTIONS("subscriptions", "Subscriptions"),
    INCOME("income", "Income"),

    /**
     * A label for an import the parser guessed at, not the accounting.
     *
     * A real transfer between the user's own accounts is a
     * [ie.shoonya.vitt.model.Transfer] and never a transaction, so it never
     * carries a category at all. This exists because a CSV row can *say*
     * "transfer" and has to land somewhere until the user reconciles it.
     */
    TRANSFER("transfer", "Transfer"),
    MISCELLANEOUS("miscellaneous", "Miscellaneous"),
    ;

    /** Categories a person picks for spending, in the order they are shown. */
    val isSpending: Boolean get() = this != INCOME && this != TRANSFER

    companion object {
        /**
         * Resolves a stored code, tolerating older and hand-typed spellings.
         *
         * The seed data and the first pass of chips used display names such as
         * `"Groceries"`, `"Family"` and `"Other"`, and those rows are already in
         * people's sheets. Matching on the label and on a couple of known
         * aliases costs nothing and keeps them categorised.
         */
        fun ofCode(code: String): Category? {
            val key = code.trim().lowercase()
            entries.firstOrNull { it.code == key }?.let { return it }
            entries.firstOrNull { it.label.lowercase() == key }?.let { return it }
            return when (key) {
                "family", "gifts", "family and gifts" -> FAMILY_GIFTS
                "other", "misc", "uncategorised", "uncategorized" -> MISCELLANEOUS
                "food" -> DINING
                "bills" -> UTILITIES
                else -> null
            }
        }
    }
}

/**
 * Which tier of the categoriser produced a category.
 *
 * `PLAN.md` §6 calls recording this *"the single best debugging affordance in the
 * system"*, after Tiller's production experience: without it, a wrong category is
 * an unexplained mystery, and with it the question is immediately either "fix the
 * rule" or "fix the seed list".
 */
enum class CategorySource(val code: String) {
    /** A rule the user taught, by correcting a merchant once. */
    LEARNED("learned"),

    /** A keyword shipped with the app. */
    SEED("seed"),

    /** Chosen by hand, with no rule behind it. */
    MANUAL("manual"),
    ;

    companion object {
        fun ofCode(code: String): CategorySource? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
