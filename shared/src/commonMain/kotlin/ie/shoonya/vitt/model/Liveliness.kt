package ie.shoonya.vitt.model

/**
 * How awake the companion is, from how recently anything was recorded.
 *
 * ## Why this is not a happy/sad scale
 *
 * The obvious design is a pet that looks sad when you stop logging. It is also
 * the single most dangerous thing that can be added to a money app, and the
 * evidence is not close.
 *
 * The documented failure mode of every budgeting app is avoidance, not
 * forgetting: people stop logging *because* they do not want to see the number
 * that will appear when they do. Shame produces avoidance, the app becomes
 * associated with feeling bad about money, and the brain stops producing the
 * experience by not opening it. `PLAN.md` §0 already names this — the ostrich
 * effect is the one robust finding the whole companion layer is built on, and
 * §5 exists to keep the layer from becoming another reason to look away.
 *
 * A sad pet is very good at driving retention in a fitness or language app,
 * where the worst case is feeling behind on French. In a money app the worst
 * case is a person who already feels bad about their finances being told, by
 * something they are fond of, that they have let it down. That is the exact
 * user who came here because other trackers made them feel worse.
 *
 * ## What replaces it
 *
 * Two rules, and everything below follows from them.
 *
 * **The companion reacts to presence, never to absence.** Showing up is
 * rewarded; being away is not punished. [PERKED] exists and no opposite of it
 * does. That asymmetry is the whole design.
 *
 * **Quiet is a state, not a verdict.** A sleeping animal is not accusing
 * anybody. [DOZING] is what a pet does when nothing is happening, and the app
 * never explains it, never counts the days aloud, and never mentions the gap on
 * return. Someone who reads guilt into a sleeping cat has brought it with them;
 * the app has not handed it to them.
 *
 * The return is the moment that matters. Come back after two weeks and the
 * companion wakes and perks, exactly as it does after one day. Being welcomed
 * back without conditions is what makes reopening cheap, and reopening is the
 * only thing that was ever in danger.
 */
enum class Liveliness {
    /**
     * Something was recorded today. Brief, and the only celebratory state.
     *
     * The reward for showing up, spent immediately rather than banked. Nothing
     * accumulates, so nothing can be lost, which means there is no streak to
     * break and no reason to avoid the app to protect one.
     */
    PERKED,

    /** Recently active. The ordinary state: up, about, unremarkable. */
    AWAKE,

    /**
     * Nothing for a while. Settled and asleep.
     *
     * Deliberately indistinguishable from a pet that is simply resting. It is
     * not a slumped posture, not a rain cloud, and not a frown. If it reads as
     * anything, it reads as calm.
     */
    DOZING,
    ;

    companion object {
        /** Beyond this, the companion settles. Two full days of quiet, not one. */
        const val DOZE_AFTER_DAYS = 3

        /**
         * [daysSinceLastRecord] is null when nothing has ever been recorded.
         *
         * A brand new install gets [AWAKE], not [DOZING]. Someone who has just
         * arrived has not been away from anything, and opening a new app to a
         * sleeping animal reads as a broken app rather than a calm one.
         */
        fun of(daysSinceLastRecord: Int?): Liveliness = when {
            daysSinceLastRecord == null -> AWAKE
            daysSinceLastRecord <= 0 -> PERKED
            daysSinceLastRecord < DOZE_AFTER_DAYS -> AWAKE
            else -> DOZING
        }
    }
}
