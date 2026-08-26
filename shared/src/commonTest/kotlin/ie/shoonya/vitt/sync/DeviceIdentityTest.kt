package ie.shoonya.vitt.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Two live devices must never share a node id.
 *
 * The node id is the last tiebreak in HLC ordering, so a shared one lets two
 * devices mint byte-identical timestamps — and because the event table keys on
 * the encoded HLC with INSERT OR IGNORE, the second device's edit is dropped on
 * ingest with nothing to show for it.
 */
class DeviceIdentityTest {

    @Test
    fun `a minted id is the right shape for an hlc`() {
        val id = DeviceIdentity.mint()
        assertEquals(Hlc.NODE_ID_LENGTH, id.length)
        assertTrue(id.all { it in "0123456789abcdef" }, "got '$id'")
        // Must be usable as-is; Hlc validates the length itself.
        Hlc(1L, 0, id)
    }

    @Test
    fun `minting twice gives different ids`() {
        val ids = (1..50).map { DeviceIdentity.mint() }.toSet()
        assertEquals(50, ids.size, "the CSPRNG must not repeat over 50 draws")
    }

    @Test
    fun `the id is stable across restarts on the same install`() {
        val driver = testDriver()
        val store = EventStore(driver)
        val first = DeviceIdentity.forStore(store, installMarker = "device-A")
        val second = DeviceIdentity.forStore(store, installMarker = "device-A")
        assertEquals(first, second, "a restart must not re-key the device")
    }

    @Test
    fun `a database restored onto another device is re-keyed`() {
        // The iOS case: Keychain and the local database both travel in an
        // encrypted backup, so restoring onto a second phone while still using
        // the first would otherwise produce two live devices with one node id.
        val driver = testDriver()
        val store = EventStore(driver)
        val original = DeviceIdentity.forStore(store, installMarker = "device-A")

        // Same database, different hardware.
        val restored = DeviceIdentity.forStore(store, installMarker = "device-B")

        assertNotEquals(original, restored, "the restored device must take a new identity")
        assertEquals(
            restored,
            DeviceIdentity.forStore(store, installMarker = "device-B"),
            "and then keep it",
        )
    }

    @Test
    fun `a corrupted stored id is replaced rather than used`() {
        // A hand-edited or truncated value would make every Hlc constructor throw.
        val driver = testDriver()
        val store = EventStore(driver)
        store.put(EventStore.KEY_NODE_ID, "too-short")
        store.put(DeviceIdentity.KEY_INSTALL_MARKER, "device-A")

        val id = DeviceIdentity.forStore(store, installMarker = "device-A")
        assertEquals(Hlc.NODE_ID_LENGTH, id.length)
    }

    @Test
    fun `two devices sharing an id would collide - which is what this prevents`() {
        // Demonstrates the failure the re-keying exists to stop.
        val shared = "a219e7a71cc18912"
        val a = HlcClock(shared, now = { 9_000L }).issue()
        val b = HlcClock(shared, now = { 9_000L }).issue()
        assertEquals(a.encode(), b.encode(), "identical id, identical instant, identical key")

        val distinct = HlcClock(DeviceIdentity.mint(), now = { 9_000L }).issue()
        assertNotEquals(a.encode(), distinct.encode())
    }
}
