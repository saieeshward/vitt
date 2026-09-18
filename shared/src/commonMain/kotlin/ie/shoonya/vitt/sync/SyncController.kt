package ie.shoonya.vitt.sync

import ie.shoonya.vitt.sheets.SheetsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Settings row says about the sheet. */
sealed interface SyncStatus {
    /** Not connected to Google. The app is local-only and that is a fine way to use it. */
    data object Off : SyncStatus

    /** Connected; nothing running. */
    data class Idle(
        /** Local changes still waiting to reach the sheet. */
        val pending: Long,
        /** Wall-clock millis of the last cycle that completed, or null before the first. */
        val lastSyncedAt: Long?,
        /** Events that will never sync because the sheet rejected them. */
        val poisoned: Int = 0,
    ) : SyncStatus

    data object Syncing : SyncStatus

    /** The last cycle stopped early. Local data is intact and the queue is kept. */
    data class Failed(val error: SheetsError, val pending: Long, val lastSyncedAt: Long?) : SyncStatus
}

/**
 * Decides *when* [Syncer] runs. The syncer decides what a run does.
 *
 * Three triggers, all funnelled through one gate so a cycle is never running
 * twice at once — two concurrent pushes would race for the same outbox rows:
 *
 * - **A local write.** Debounced, because an amount corrected three times in
 *   ten seconds should reach the sheet as one batch, and each append costs a
 *   quota write whether it carries one row or five hundred.
 * - **Coming to the foreground.** Immediate. This is when another device's
 *   changes are most likely waiting, and when the user is most likely to be
 *   looking for them.
 * - **The Settings button.** Immediate, and the only one that reports an error
 *   to the user's face; the automatic ones show it quietly in the status row.
 *
 * Every trigger is a no-op while disconnected. Local writes still queue in the
 * outbox, so connecting later drains everything that was ever recorded.
 */
class SyncController(
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val syncer: () -> Syncer,
    private val store: EventStore,
    private val now: () -> Long,
    /** How long after the last local write a sync starts. */
    private val debounceMillis: Long = 2_000,
) {
    private val _status = MutableStateFlow<SyncStatus>(if (isConnected()) idle(null) else SyncStatus.Off)
    val status: StateFlow<SyncStatus> = _status

    /**
     * Incremented whenever a cycle brought new events in from the sheet. The
     * UI keys its reads on this, so a pull repaints the screens without the
     * controller having to know what a screen is.
     */
    private val _remoteChanges = MutableStateFlow(0)
    val remoteChanges: StateFlow<Int> = _remoteChanges

    private val gate = Mutex()
    private var debounced: Job? = null
    private var lastSyncedAt: Long? = null

    /** A local write happened. Coalesces with any other in the debounce window. */
    fun onLocalWrite() {
        if (!isConnected()) return
        debounced?.cancel()
        debounced = scope.launch {
            delay(debounceMillis)
            runCycle()
        }
    }

    /** The app came to the foreground, or was connected just now. */
    fun onForeground() {
        if (!isConnected()) { _status.value = SyncStatus.Off; return }
        scope.launch { runCycle() }
    }

    /** The user asked. Returns the outcome so a screen can speak to it. */
    suspend fun syncNow(): SyncOutcome? {
        if (!isConnected()) { _status.value = SyncStatus.Off; return null }
        debounced?.cancel()
        return runCycle()
    }

    /** Reflects a disconnect. The outbox is kept: it drains on the next connect. */
    fun onDisconnected() {
        debounced?.cancel()
        _status.value = SyncStatus.Off
    }

    private suspend fun runCycle(): SyncOutcome = gate.withLock {
        _status.value = SyncStatus.Syncing
        // sync() reports its own failures in the outcome, but a bug or a
        // transport that throws something unexpected must not leave the status
        // on Syncing forever. Cancellation is the one thing allowed through.
        val outcome = try {
            syncer().sync()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SheetsError) {
            SyncOutcome(error = e)
        } catch (e: Throwable) {
            SyncOutcome(error = SheetsError.Transport(e.message ?: e::class.simpleName ?: "failed", e))
        }
        if (outcome.pulled > 0) _remoteChanges.value++
        val error = outcome.error
        if (error == null) {
            lastSyncedAt = now()
            _status.value = idle(lastSyncedAt)
        } else {
            _status.value = SyncStatus.Failed(error, store.pendingCount(), lastSyncedAt)
        }
        outcome
    }

    private fun idle(at: Long?) = SyncStatus.Idle(
        pending = store.pendingCount(),
        lastSyncedAt = at,
        poisoned = store.poisoned().size,
    )
}
