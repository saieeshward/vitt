package ie.shoonya.vitt.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LivelinessTest {

    @Test
    fun `recording today perks the companion up`() {
        // The only celebratory state, and it is spent immediately rather than
        // banked. Nothing accumulates, so nothing can be lost.
        assertEquals(Liveliness.PERKED, Liveliness.of(0))
    }

    @Test
    fun `a day or two of quiet is still awake`() {
        assertEquals(Liveliness.AWAKE, Liveliness.of(1))
        assertEquals(Liveliness.AWAKE, Liveliness.of(2))
    }

    @Test
    fun `longer quiet settles rather than sulks`() {
        // DOZING is a sleeping animal, not a slumped one. The app never counts
        // the days aloud and never mentions the gap.
        assertEquals(Liveliness.DOZING, Liveliness.of(3))
        assertEquals(Liveliness.DOZING, Liveliness.of(30))
        assertEquals(Liveliness.DOZING, Liveliness.of(365))
    }

    @Test
    fun `a fresh install is awake — it has not been away from anything`() {
        // Opening a brand new app to a sleeping animal reads as broken, not calm.
        assertEquals(Liveliness.AWAKE, Liveliness.of(null))
    }

    @Test
    fun `there is no state worse than dozing`() {
        // The asymmetry is the design: presence is rewarded, absence is never
        // punished. A year away and a week away look identical, because the
        // app has nothing to say about either.
        val longestAway = Liveliness.of(10_000)
        assertEquals(Liveliness.DOZING, longestAway)
        assertEquals(
            Liveliness.entries.size,
            3,
            "a fourth, unhappier state would reintroduce the shame this model exists to avoid",
        )
    }

    @Test
    fun `coming back is welcomed identically however long the gap`() {
        // "Welcomes you back without shame" made literal: the state after
        // recording is PERKED whether the last entry was yesterday or never.
        assertEquals(Liveliness.of(0), Liveliness.PERKED)
    }

    @Test
    fun `a clock that went backwards does not produce a new state`() {
        // Days-since is derived from two dates and a device clock can move
        // backwards, so a negative is possible and must land somewhere sane
        // rather than falling through to DOZING.
        assertEquals(Liveliness.PERKED, Liveliness.of(-1))
        assertEquals(Liveliness.PERKED, Liveliness.of(-400))
    }
}
