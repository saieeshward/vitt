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
    /**
     * Rebuilds the human-readable tabs once the event exchange has succeeded.
     *
     * A hook rather than a step inside [Syncer] because the two are different
     * jobs that happen to share a trip to the network. The syncer moves events
     * and is the part that must be exactly right about idempotency and
     * ordering; this is a projection of local state onto a tab, and it is
     * allowed to fail without the sync having failed.
     *
     * After, never before. Rendering the fold before the pull has landed would
     * publish a table that is already out of date, and publish it as the
     * authoritative-looking one.
     */
    private val refreshDerived: suspend () -> Unit = {},
    /**
     * The shortest gap between two derived-tab refreshes.
     *
     * A refresh is about eight requests (the `_Meta` read, the Transactions
     * read, write and clear, and a write and clear per year of summaries) against
     * a quota of sixty a minute per user. With a sync after every pause in
     * typing, somebody entering a week of receipts would spend the whole quota
     * redrawing tabs nobody is looking at, and the event appends that actually
     * matter would start coming back 429. So the first refresh runs at once and
     * the rest wait out this window, folded into one at the end of it.
     */
    private val derivedCooldownMillis: Long = 30_000,
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

    /**
     * Whether the tabs are behind the fold. True at launch, because a person
     * may have edited the sheet while the app was closed and only a refresh
     * finds out.
     */
    private var derivedStale = true
    private var derivedCooling: Job? = null

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
        // The one trigger that skips the cooldown: somebody pressed a button
        // and is looking at the spreadsheet to see it work.
        return runCycle(forceDerived = true)
    }

    /** Reflects a disconnect. The outbox is kept: it drains on the next connect. */
    fun onDisconnected() {
        debounced?.cancel()
        _status.value = SyncStatus.Off
    }

    private suspend fun runCycle(forceDerived: Boolean = false): SyncOutcome = gate.withLock {
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
            // A cycle that moved nothing leaves the fold where it was, so the
            // tabs are exactly as current as they were.
            if (outcome.pushed > 0 || outcome.pulled > 0) derivedStale = true
            if (forceDerived) derivedCooling?.cancel()
            if (derivedStale && (forceDerived || derivedCooling?.isActive != true)) refreshDerivedNow()
            lastSyncedAt = now()
            _status.value = idle(lastSyncedAt)
        } else {
            _status.value = SyncStatus.Failed(error, store.pendingCount(), lastSyncedAt)
        }
        outcome
    }

    /**
     * Refreshes and starts the cooldown. Called with [gate] held, so a refresh
     * never overlaps a cycle.
     *
     * Failures are swallowed deliberately. The events are in the sheet, which
     * is the thing that must not be lost; a tab that failed to redraw is redrawn
     * later. Reporting this as a sync failure would put an error in front of
     * somebody whose data is perfectly safe, and leave the outbox looking
     * undrained.
     */
    private suspend fun refreshDerivedNow() {
        derivedStale = false
        try {
            refreshDerived()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            derivedStale = true
        }
        derivedCooling = scope.launch {
            delay(derivedCooldownMillis)
            // Whatever changed during the window, drawn once at its end.
            gate.withLock { if (derivedStale) refreshDerivedNow() }
        }
    }

    private fun idle(at: Long?) = SyncStatus.Idle(
        pending = store.pendingCount(),
        lastSyncedAt = at,
        poisoned = store.poisoned().size,
    )
}
