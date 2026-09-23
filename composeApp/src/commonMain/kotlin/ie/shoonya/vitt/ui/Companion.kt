package ie.shoonya.vitt.ui

/**
 * The six companion animals from the design's artboard 14a.
 *
 * These are the design's own six, not a set of my own: an earlier pass here
 * invented five animals (an owl, a fox, a frog) that were never in the design
 * and gave each of them the pig's coin slots, which is exactly the failure 14a
 * uses to reject the cat — a currency marker bolted onto an animal that has no
 * relationship to it explains nothing.
 *
 * **The rule they are judged on: n stashes, or it fails.** Whatever holds the
 * currencies has to repeat without redesign and stay legible at 20px. Slots,
 * acorns, birds and bones all do. Two of the six do not, and they are marked
 * [ruledOut] rather than deleted: 14a keeps them as unlockable skins for once
 * the currency markers live somewhere other than the animal.
 *
 * The art, the colours and the rig all come from [CompanionSprites]. Each animal
 * has its own authored palette, so the animal is not merely a silhouette — that
 * was another of my inventions, and the design is better for having six
 * differently coloured creatures rather than six recolours of one.
 */
enum class CompanionAnimal(
    val code: String,
    val label: String,
    val species: String,
    /** How this animal carries the currencies, which is the whole test. */
    val marker: String,
    /**
     * Fails the n-stashes rule, and why.
     *
     * Null when it passes. Kept selectable, because 14a's own note is that both
     * are charming and both should survive as cosmetics.
     */
    val ruledOut: String? = null,
) {
    /** 14a's pick. "A piglet already has the proportions." */
    PENNY("penny", "Penny", "pig", "coin slots on her back"),

    /** The best metaphor: many separate stashes, each remembered. */
    ACORN("acorn", "Acorn", "squirrel", "acorns on her back"),

    /** The close second, and the cheapest to scale: one more bird per currency. */
    KUMO("kumo", "Kumo", "capybara", "birds riding along"),

    /** Warm and readable; 14a's honest objection is that a dog is generic. */
    BARLEY("barley", "Barley", "dog", "bones she buried"),

    /** Two cheeks, so two pockets, forever. */
    BISCUIT(
        "biscuit", "Biscuit", "hamster", "full cheeks",
        ruledOut = "Only ever two pockets",
    ),

    /** The tags are single pixels on a one-pixel collar. */
    MOCHI(
        "mochi", "Mochi", "cat", "tags on her collar",
        ruledOut = "Currencies are hard to read",
    ),
    ;

    /** True for the four that can carry any number of currencies. */
    val carriesAnyCurrency: Boolean get() = ruledOut == null

    companion object {
        /** 14a: "Where I'd land — Penny, with Kumo a close second." */
        val DEFAULT = PENNY

        /**
         * The stored value for "no pet at all".
         *
         * Deliberately separate from the gamification switch: someone can want
         * the streak counted and not want an animal on their screen, and making
         * them turn off the whole layer to be rid of it would be the punished
         * mode §5.7 forbids.
         */
        const val NONE = "none"

        /**
         * Resolves a stored code. Null means the user chose no pet.
         *
         * An unrecognised value falls back to the default rather than to null:
         * it may name an animal a newer build has, and a missing pet reads as
         * "the app lost my choice" where the wrong one reads as "not mine".
         */
        fun ofCode(code: String?): CompanionAnimal? {
            val key = code?.trim()?.lowercase()
            if (key == NONE) return null
            return entries.firstOrNull { it.code == key } ?: DEFAULT
        }
    }
}
