package ie.shoonya.tracker.sync

/**
 * Folds an append-only event log into current state.
 *
 * Per-field last-write-wins: for each (entity, entityId, field), the event with
 * the greatest HLC wins. This is a CRDT — a map of last-write-wins registers is
 * convergent — just the cheap variety, with one timestamp per field rather than
 * per edit.
 *
 * Convergence is the property that matters: any two devices that have seen the
 * same set of events fold them into identical state, regardless of the order the
 * events arrived in. That is what allows the sheet to be a dumb append-only log
 * with no server arbitrating writes.
 */
object EventLog {

    /** A folded entity: field name to winning value. */
    data class Entity(
        val entity: String,
        val entityId: String,
        val fields: Map<String, TaggedValue>,
        val fieldClocks: Map<String, Hlc>,
        val deleted: Boolean,
    )

    const val TOMBSTONE_FIELD = "_deleted"

    fun fold(events: Iterable<Event>): Map<String, Entity> {
        val winners = HashMap<Pair<String, String>, MutableMap<String, Event>>()

        for (event in events) {
            val key = event.entity to event.entityId
            val fields = winners.getOrPut(key) { HashMap() }
            val existing = fields[event.field]
            // Strictly greater: an identical HLC means the same event seen twice,
            // so keeping the first is what makes replay idempotent.
            if (existing == null || event.hlc > existing.hlc) {
                fields[event.field] = event
            }
        }

        return winners.entries.associate { (key, fields) ->
            val (entity, entityId) = key
            entityId to Entity(
                entity = entity,
                entityId = entityId,
                fields = fields.mapValues { it.value.value }
                    .filterKeys { it != TOMBSTONE_FIELD },
                fieldClocks = fields.mapValues { it.value.hlc },
                deleted = (fields[TOMBSTONE_FIELD]?.value as? TaggedValue.Bool)?.value == true,
            )
        }
    }

    /**
     * Events whose HLC is not already present locally.
     *
     * Deduplication is by HLC alone, never by content: two genuinely separate
     * EUR 3.50 coffees bought on the same day at the same shop are distinct
     * events that must both survive, and a content hash would silently collapse
     * them into one.
     */
    fun newEvents(incoming: Iterable<Event>, knownHlcs: Set<String>): List<Event> {
        val seen = knownHlcs.toMutableSet()
        // Deduplicates within the batch as well as against what is already known:
        // a paginated read that overlaps, or a sheet a user has copy-pasted rows
        // in, can present the same event twice in a single pass.
        return incoming.filter { seen.add(it.hlc.encode()) }
    }
}
