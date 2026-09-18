package ie.shoonya.vitt.text

import kotlin.test.Test
import kotlin.test.assertEquals

class CharsTest {
    @Test
    fun `a cap never splits a surrogate pair`() {
        val note = "a".repeat(79) + "\uD83C\uDF82" // 79 letters then a cake emoji
        val cut = note.takeChars(80)
        assertEquals(79, cut.length)
        assertEquals("a".repeat(79), cut)
        assertEquals("abc", "abc".takeChars(80))
        assertEquals("ab", "abc".takeChars(2))
    }
}
