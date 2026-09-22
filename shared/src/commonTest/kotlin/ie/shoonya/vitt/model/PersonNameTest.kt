package ie.shoonya.vitt.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonNameTest {

    @Test
    fun `an address becomes the name in front of it`() {
        assertEquals("Anya", PersonName.of("anya@example.com"))
        assertEquals("anya@example.com", PersonName.address("anya@example.com"))
    }

    @Test
    fun `separators become spaces and each word is capitalised`() {
        assertEquals("Anya Smith", PersonName.of("anya.smith@example.com"))
        assertEquals("Anya Smith", PersonName.of("anya_smith@example.com"))
        assertEquals("Anya Smith", PersonName.of("anya-smith@example.com"))
    }

    @Test
    fun `plus addressing is routing rather than identity`() {
        assertEquals("Anya", PersonName.of("anya+taxi@example.com"))
    }

    @Test
    fun `a name typed as a name is left exactly alone`() {
        assertEquals("Anya", PersonName.of("Anya"))
        assertEquals("Mrs O'Leary", PersonName.of("Mrs O'Leary"))
        assertNull(PersonName.address("Anya"))
    }

    @Test
    fun `an identifier is left as the address rather than dressed up`() {
        // Title-casing a number produces something that looks like a name and
        // is not one, and a wrong guess is worse than an honest address.
        assertEquals("100293847@example.com", PersonName.of("100293847@example.com"))
    }

    @Test
    fun `nothing sensible in means nothing invented out`() {
        assertEquals("", PersonName.of(""))
        assertEquals("@example.com", PersonName.of("@example.com"))
        assertNull(PersonName.address("@example.com"))
    }
}
