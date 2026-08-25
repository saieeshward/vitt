package ie.shoonya.vitt.sync

/**
 * A stand-in for the Events tab of a Google Sheet, with the same guarantees the
 * real API gives and — importantly — none of the ones it does not.
 *
 * Modelled faithfully:
 *  - append is atomic per call and always adds to the end
 *  - there is no upsert, no conditional write, no compare-and-swap
 *  - a write can succeed on the server while the client never learns it did
 *
 * That last case is the whole reason this class exists. [appendCrashingAfterWrite]
 * reproduces the failure a real network partition causes: the rows are committed,
 * the response is lost.
 */
class FakeSheet {
    private val rows = mutableListOf<List<String>>()
    var appendCalls = 0; private set

    val rowCount: Int get() = rows.size

    fun append(newRows: List<List<String>>) {
        appendCalls++
        rows.addAll(newRows)
    }

    /** Commits the rows, then fails as if the response never arrived. */
    fun appendCrashingAfterWrite(newRows: List<List<String>>): Nothing {
        append(newRows)
        throw TransportLost("connection dropped after the server committed")
    }

    /** Fails without committing, as a rejected request would. */
    fun appendFailingBeforeWrite(): Nothing {
        appendCalls++
        throw TransportLost("connection refused")
    }

    fun readAll(): List<Event> = rows.map { Event.fromRow(it) }

    fun knownHlcs(): Set<String> = rows.map { it[0] }.toSet()

    /** How the client checks whether specific events landed, cheaply. */
    fun tailHlcs(count: Int): Set<String> = rows.takeLast(count).map { it[0] }.toSet()

    fun duplicateHlcs(): List<String> =
        rows.map { it[0] }.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toList()
}

class TransportLost(message: String) : Exception(message)
