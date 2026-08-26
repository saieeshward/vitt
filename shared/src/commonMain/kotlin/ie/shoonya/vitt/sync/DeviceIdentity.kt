package ie.shoonya.vitt.sync

import ie.shoonya.vitt.auth.Crypto

/**
 * This device's identity within the ledger.
 *
 * The node id is the final tiebreak in [Hlc.compareTo], which means two devices
 * sharing one can mint byte-identical timestamps. The event table keys on the
 * encoded HLC with `INSERT OR IGNORE`, so the loser is dropped on ingest without
 * a trace — a whole device's edits vanishing silently.
 *
 * That is not hypothetical. On iOS the Keychain credential and the local
 * database both travel in an encrypted backup, so restoring a backup onto a
 * second phone while still using the first produces exactly this: two live
 * devices, one node id.
 */
object DeviceIdentity {

    const val LENGTH = Hlc.NODE_ID_LENGTH

    /** 64 bits from the platform CSPRNG — collision is not a practical concern. */
    fun mint(): String = Crypto.randomBytes(8)
        .joinToString("") { byte ->
            val v = byte.toInt() and 0xFF
            v.toString(16).padStart(2, '0')
        }

    /**
     * Returns this device's node id, minting and storing one on first run.
     *
     * @param installMarker distinguishes this installation from one restored
     * from a backup. Stored beside the node id; if the stored marker belongs to
     * a different installation, the node id came from someone else's device and
     * must be re-minted before anything is written.
     */
    fun forStore(store: EventStore, installMarker: String): String {
        val storedNode = store.get(EventStore.KEY_NODE_ID)
        val storedMarker = store.get(KEY_INSTALL_MARKER)

        val needsMint = storedNode == null ||
            storedNode.length != LENGTH ||
            storedMarker == null ||
            storedMarker != installMarker

        if (!needsMint) return storedNode!!

        // A restored backup arrives with someone else's node id and their
        // install marker. Re-minting here is what stops the two devices from
        // silently overwriting each other's history.
        val fresh = mint()
        store.put(EventStore.KEY_NODE_ID, fresh)
        store.put(KEY_INSTALL_MARKER, installMarker)
        return fresh
    }

    const val KEY_INSTALL_MARKER = "install_marker"
}
