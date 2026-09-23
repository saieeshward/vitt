package ie.shoonya.vitt.sheets

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetMetaTest {

    private val us = SheetMeta.Us(device = "aaaa000000000001", appVersion = "1.0.0", nowMillis = 0)

    private fun tab(vararg rows: List<String>) = listOf(SheetMeta.COLUMNS) + rows.toList()

    private fun stamp(key: String, value: String, device: String = us.device) =
        listOf(key, value, device, "1970-01-01T00:00:00.000Z")

    private class FakePort(var contents: List<List<String>> = emptyList(), val locale: String? = "en_GB") : MetaPort {
        val calls = mutableListOf<String>()
        val appended = mutableListOf<List<String>>()
        var localeReads = 0
        override suspend fun read(spreadsheetId: String, tab: String): List<List<String>> {
            calls += "read"
            return contents
        }
        override suspend fun append(spreadsheetId: String, tab: String, rows: List<List<String>>) {
            calls += "append"
            appended += rows
            contents = contents + rows
        }
        override suspend fun locale(spreadsheetId: String): String? {
            localeReads++
            return locale
        }
    }

    @Test
    fun `a file from before _Meta gets a header and all three stamps`() = runTest {
        val port = FakePort()
        val standing = SheetMeta.sync(port, "s", us)

        assertEquals(SheetMeta.Standing.Current, standing)
        assertEquals(SheetMeta.COLUMNS, port.appended.first())
        assertEquals(
            listOf("schema_version", "app_version", "locale"),
            port.appended.drop(1).map { it.first() },
        )
    }

    @Test
    fun `a second sync writes nothing at all`() = runTest {
        // Every sync runs this, so the ordinary answer has to be no request.
        val port = FakePort()
        SheetMeta.sync(port, "s", us)
        port.calls.clear()

        SheetMeta.sync(port, "s", us)

        assertEquals(listOf("read"), port.calls)
    }

    @Test
    fun `the locale is only asked for when the caller wants it checked`() = runTest {
        val port = FakePort()
        SheetMeta.sync(port, "s", us, checkLocale = false)
        assertEquals(0, port.localeReads)
        assertTrue(port.appended.none { it.first() == "locale" })
    }

    @Test
    fun `an update is one new row and nothing edited`() = runTest {
        // Append-only: the old version stays as history, and a later row of the
        // same key is what supersedes it.
        val port = FakePort(tab(stamp("schema_version", "1"), stamp("app_version", "0.9.0"), stamp("locale", "en_GB")))
        SheetMeta.sync(port, "s", us)
        assertEquals(listOf(stamp("app_version", "1.0.0")), port.appended)
        assertEquals("1.0.0", SheetMeta.read(port.contents).devices[us.device])
    }

    @Test
    fun `the registry holds each device's latest version`() {
        val state = SheetMeta.read(
            tab(
                stamp("app_version", "1.0.0", "phone"),
                stamp("app_version", "1.0.0", "tablet"),
                stamp("app_version", "1.1.0", "phone"),
            ),
        )
        assertEquals(mapOf("phone" to "1.1.0", "tablet" to "1.0.0"), state.devices)
    }

    @Test
    fun `a newer schema means the derived tabs are left alone`() {
        // An older build would read the newer header as unusable and archive it
        // away, and the newer phone would do the same back. The number is what
        // stops that.
        val state = SheetMeta.read(tab(stamp("schema_version", "2", "newer-phone")))
        assertEquals(SheetMeta.Standing.Newer(2), SheetMeta.standing(state, us))
    }

    @Test
    fun `an older build stamping late never lowers the file's version`() {
        val state = SheetMeta.read(
            tab(stamp("schema_version", "2", "newer-phone"), stamp("schema_version", "1", "old-phone")),
        )
        assertEquals(2, state.schemaVersion)
        assertTrue(SheetMeta.due(state, us, "en_GB").none { it.first() == "schema_version" })
    }

    @Test
    fun `a changed locale is stamped again`() {
        // §2.4: recorded so a mismatch can be seen rather than silently mis-parsed.
        val state = SheetMeta.read(tab(stamp("schema_version", "1"), stamp("app_version", "1.0.0"), stamp("locale", "en_GB")))
        assertEquals(listOf(stamp("locale", "de_DE")), SheetMeta.due(state, us, "de_DE"))
    }

    @Test
    fun `columns are found by name`() {
        val reordered = listOf(listOf("written_at", "device", "value", "key", "Notes")) +
            listOf(listOf("x", "phone", "1.2.0", "app_version", "mine"))
        assertEquals(mapOf("phone" to "1.2.0"), SheetMeta.read(reordered).devices)
    }

    @Test
    fun `a tab somebody else filled is not written into`() = runTest {
        // Rows with no header this code can find: appending under them would
        // mix our stamps into whatever that is.
        val port = FakePort(listOf(listOf("my notes"), listOf("groceries are up")))
        val standing = SheetMeta.sync(port, "s", us)
        assertEquals(SheetMeta.Standing.Current, standing)
        assertTrue(port.appended.isEmpty())
    }

    @Test
    fun `a junk version is ignored rather than trusted`() {
        val state = SheetMeta.read(tab(stamp("schema_version", "two")))
        assertEquals(null, state.schemaVersion)
        assertEquals(SheetMeta.Standing.Current, SheetMeta.standing(state, us))
    }
}
