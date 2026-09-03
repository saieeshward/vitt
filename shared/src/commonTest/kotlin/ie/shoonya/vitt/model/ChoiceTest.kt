package ie.shoonya.vitt.model

import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChoiceTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    @Test
    fun `an unset choice is null — the default`() {
        assertNull(repo().choice(Choice.COMPANION))
    }

    @Test
    fun `a choice reads back`() {
        val r = repo()
        r.setChoice(Choice.COMPANION, "owl")
        assertEquals("owl", r.choice(Choice.COMPANION))
    }

    @Test
    fun `choosing twice edits rather than duplicates`() {
        // The key is the entity id, so this has to be an edit however many
        // devices set it.
        val r = repo()
        r.setChoice(Choice.THEME, "cream")
        r.setChoice(Choice.THEME, "slate")
        assertEquals(1, r.choices().size)
        assertEquals("slate", r.choice(Choice.THEME))
    }

    @Test
    fun `choices are independent of each other`() {
        val r = repo()
        r.setChoice(Choice.COMPANION, "cat")
        r.setChoice(Choice.THEME, "paper")
        r.setChoice(Choice.ACCENT, "clay")
        assertEquals(3, r.choices().size)
        assertEquals("cat", r.choice(Choice.COMPANION))
        assertEquals("paper", r.choice(Choice.THEME))
        assertEquals("clay", r.choice(Choice.ACCENT))
    }

    @Test
    fun `clearing a choice returns it to the default`() {
        val r = repo()
        r.setChoice(Choice.ACCENT, "plum")
        r.clearChoice(Choice.ACCENT)
        assertNull(r.choice(Choice.ACCENT))
    }

    @Test
    fun `an unrecognised value is kept rather than dropped`() {
        // It may be a newer build's option arriving from the user's other phone.
        // Rewriting it to the default here would lose their choice permanently.
        val r = repo()
        r.setChoice(Choice.COMPANION, "axolotl-from-a-later-version")
        assertEquals("axolotl-from-a-later-version", r.choice(Choice.COMPANION))
    }

    @Test
    fun `a choice does not collide with a preference of the same key`() {
        // Different entities, so the switch and the named choice cannot overwrite
        // one another even when a key is reused.
        val r = repo()
        r.setPreference("mode", true)
        r.setChoice("mode", "quiet")
        assertEquals(true, r.preferences()["mode"])
        assertEquals("quiet", r.choice("mode"))
    }

    @Test
    fun `a blank key or value is a caller bug`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> { r.setChoice("", "owl") }
        assertFailsWith<IllegalArgumentException> { r.setChoice(Choice.COMPANION, "") }
    }

    @Test
    fun `gamification staying off does not disturb a choice`() {
        // The two live in different entities, so switching the habit layer off
        // must not clear the animal the user picked.
        val r = repo()
        r.setChoice(Choice.COMPANION, "fox")
        r.setGamificationEnabled(false)
        assertEquals("fox", r.choice(Choice.COMPANION))
        assertTrue(!r.gamificationEnabled())
    }
}
