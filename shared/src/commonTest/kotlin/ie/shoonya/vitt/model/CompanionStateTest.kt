package ie.shoonya.vitt.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionStateTest {

    private fun face(
        liveliness: Liveliness,
        justSaved: Boolean = false,
        needsAttention: Int = 0,
        anyBudgetSpent: Boolean = false,
    ) = CompanionFace.of(liveliness, justSaved, needsAttention, anyBudgetSpent)

    @Test
    fun `rest outranks every concern`() {
        // Someone back after two weeks is met by a sleeping animal, not by a
        // list of what went wrong while they were away.
        assertEquals(
            CompanionFace.SLEEPY,
            face(Liveliness.DOZING, needsAttention = 9, anyBudgetSpent = true),
        )
    }

    @Test
    fun `logging is never answered with bad news`() {
        // The trap this avoids: greeting the act of recording with a warning
        // teaches that opening the app produces an unpleasant feeling, and the
        // documented response to that is to stop opening it.
        assertEquals(
            CompanionFace.GLAD,
            face(Liveliness.PERKED, justSaved = true, needsAttention = 4, anyBudgetSpent = true),
        )
    }

    @Test
    fun `a pile to sort reads as a task rather than a failing`() {
        assertEquals(CompanionFace.PECKISH, face(Liveliness.AWAKE, needsAttention = 3))
    }

    @Test
    fun `an empty pocket is stated once the pile is clear`() {
        assertEquals(
            CompanionFace.CAREFUL,
            face(Liveliness.AWAKE, needsAttention = 0, anyBudgetSpent = true),
        )
    }

    @Test
    fun `awake with nothing pending waits rather than reproaches`() {
        assertEquals(CompanionFace.EXPECT, face(Liveliness.AWAKE))
    }

    @Test
    fun `a finished day is allowed to be uneventful`() {
        // Recorded today, nothing pending, nothing overspent. The only state
        // that says "there is nothing here for you", and an app that can say
        // that honestly is worth more than one that always finds something.
        assertEquals(CompanionFace.CONTENT, face(Liveliness.PERKED))
    }

    @Test
    fun `no input combination produces a face that blames the user`() {
        // The whole model, asserted as a property: every reachable state points
        // at a thing on the screen, and none of the six is a judgement about a
        // person. If a seventh, unhappier face is ever added, this fails.
        val reachable = buildSet {
            Liveliness.entries.forEach { live ->
                listOf(0, 1, 5).forEach { pending ->
                    listOf(false, true).forEach { spent ->
                        add(CompanionFace.of(live, false, pending, spent))
                    }
                }
            }
        }
        assertEquals(
            setOf(
                CompanionFace.SLEEPY,
                CompanionFace.PECKISH,
                CompanionFace.CAREFUL,
                CompanionFace.EXPECT,
                CompanionFace.CONTENT,
            ),
            reachable,
            "an unexpected face became reachable — check it is not a reproach",
        )
    }

    @Test
    fun `every face points at its own subject`() {
        // Movement is the message: she stands next to the thing she means.
        assertEquals(CompanionWhere.ACTIVITY, CompanionFace.PECKISH.whereTo())
        assertEquals(CompanionWhere.ADD, CompanionFace.EXPECT.whereTo())
        assertEquals(CompanionWhere.SPENT_LEDGER, CompanionFace.CAREFUL.whereTo())
    }

    @Test
    fun `a quiet screen sends her home rather than wandering`() {
        // Stillness is the honest answer to a quiet day, and an app willing to
        // be still can be trusted when it moves.
        assertEquals(CompanionWhere.HOME, CompanionFace.CONTENT.whereTo())
        assertEquals(CompanionWhere.HOME, CompanionFace.SLEEPY.whereTo())
    }

    @Test
    fun `the reward happens where the user is already looking`() {
        // Walking off to celebrate would break the connection between the act
        // and the response.
        assertEquals(CompanionWhere.STAY, CompanionFace.GLAD.whereTo())
    }
}
