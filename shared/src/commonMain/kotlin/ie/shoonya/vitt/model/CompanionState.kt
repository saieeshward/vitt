package ie.shoonya.vitt.model

/**
 * What the companion is expressing, chosen from what is actually true.
 *
 * The six faces already exist in the sprite table, each with a documented
 * trigger, and until now nothing drove any of them: the animal wandered with a
 * single neutral face. This is the missing half.
 *
 * ## The rule everything follows from
 *
 * **The companion may be interested in a thing. It is never disappointed in a
 * person.** Every state below points at something on the screen — an entry
 * waiting to be categorised, a pocket running low, a day with nothing in it —
 * and none of them is a judgement about the user. That distinction is what
 * keeps this on the right side of §5 and of the ostrich effect that
 * [Liveliness] documents at length: an app that makes someone feel watched is
 * an app they stop opening, and in a money app the stakes of that are the whole
 * product.
 *
 * ## Why the order is the order
 *
 * Only one face can show, so precedence is the design.
 *
 * Rest wins over everything. Someone returning after two weeks is met by a
 * sleeping animal, not by a list of what went wrong while they were away.
 *
 * Then the reward. If an entry has just been recorded the face is [GLAD], even
 * when a budget is over. Greeting the act of logging with bad news is precisely
 * the trap: it teaches that opening the app produces an unpleasant feeling, and
 * the documented response to that is to stop opening it. The concern can wait
 * until the next visit; it is not going anywhere.
 *
 * Only then the things that want attention, and only then the quiet defaults.
 */
enum class CompanionFace {
    /** Asleep. Quiet is a state, never a verdict. */
    SLEEPY,

    /** Something was just recorded. The reward for turning up. */
    GLAD,

    /** Entries are waiting to be confirmed or categorised. */
    PECKISH,

    /** A budget is spent. A fact about a pocket, not about a person. */
    CAREFUL,

    /** Up and waiting, with nothing recorded yet today. Anticipation, not reproach. */
    EXPECT,

    /** Today is done and nothing needs doing. The settled, finished state. */
    CONTENT,
    ;

    companion object {
        /**
         * @param liveliness how recently anything was recorded
         * @param justSaved true for the moment right after an entry is saved
         * @param needsAttention entries the user still has to resolve
         * @param anyBudgetSpent true when a budget has nothing left in it
         *
         * [justSaved] is separate from [Liveliness.PERKED] because they answer
         * different questions. PERKED is "something happened today" and lasts
         * until midnight; justSaved is "something happened a second ago" and is
         * the actual reward. Without the distinction the glad face would sit
         * there all evening, which turns a reaction into a decoration.
         */
        fun of(
            liveliness: Liveliness,
            justSaved: Boolean = false,
            needsAttention: Int = 0,
            anyBudgetSpent: Boolean = false,
        ): CompanionFace = when {
            // Rest first. Nothing is urgent enough to wake the animal up to
            // deliver, and a person coming back deserves to be met by calm.
            liveliness == Liveliness.DOZING -> SLEEPY

            // The reward, before any concern. Logging must never be answered
            // with bad news, or logging becomes the thing that stops.
            justSaved -> GLAD

            // Actionable and neutral: there is a pile to sort, which is a task
            // rather than a failing.
            needsAttention > 0 -> PECKISH

            // A pocket is empty. Said plainly, once, and never repeated at
            // someone who has just recorded something.
            anyBudgetSpent -> CAREFUL

            // Awake, nothing yet today. A dog at the door, not a raised
            // eyebrow: it expresses interest in what is coming rather than
            // dissatisfaction with what has not happened.
            liveliness == Liveliness.AWAKE -> EXPECT

            // Recorded today, nothing pending, nothing overspent. The day is
            // done. This is the only state that says "there is nothing here
            // for you", and an app that can say that honestly is worth more
            // than one that always finds something.
            else -> CONTENT
        }
    }
}

/**
 * Where the companion goes, which is the other half of what it means.
 *
 * Movement is the message. An animal that wanders at random is decoration, and
 * decoration on a screen full of numbers is noise; an animal that walks to a
 * thing and stands next to it has said something without a word of copy. So
 * every destination below is the subject of the matching face: [PECKISH] goes
 * to where the pile is, [EXPECT] waits by the button that clears it, [CAREFUL]
 * stands by the pocket it means.
 *
 * And when there is nothing to point at, she goes home and settles. Home is
 * wherever the user last put her, because a pet that returns to the spot you
 * chose is a pet that belongs to you. Stillness is the honest answer to a quiet
 * day, and an app willing to be still is one that can be trusted when it moves.
 */
enum class CompanionWhere {
    /** The user's chosen spot. Rest, and the default for a quiet screen. */
    HOME,

    /** Do not travel. Used for the moment after a save, where she already is. */
    STAY,

    /** The add button: waiting by the door for the thing she expects. */
    ADD,

    /** The activity list, where entries wanting attention are. */
    ACTIVITY,

    /** The card for the currency whose budget is spent. */
    SPENT_LEDGER,
}

/** Where this face sends her. */
fun CompanionFace.whereTo(): CompanionWhere = when (this) {
    // Asleep and done for the day both mean the same thing: be where you live.
    CompanionFace.SLEEPY -> CompanionWhere.HOME
    CompanionFace.CONTENT -> CompanionWhere.HOME

    // The reward happens where the user is looking, which is wherever she
    // already stands. Walking off to celebrate would break the connection
    // between the act and the response.
    CompanionFace.GLAD -> CompanionWhere.STAY

    CompanionFace.PECKISH -> CompanionWhere.ACTIVITY
    CompanionFace.CAREFUL -> CompanionWhere.SPENT_LEDGER
    CompanionFace.EXPECT -> CompanionWhere.ADD
}
