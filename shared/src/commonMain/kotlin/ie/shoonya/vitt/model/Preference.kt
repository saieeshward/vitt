package ie.shoonya.vitt.model

import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue

/**
 * A user preference, carried in the event log like everything else.
 *
 * Synced rather than device-local, because someone who switches gamification off
 * means it off — and having it come back on a second device would be the app
 * overriding a decision they already made. `PLAN.md:222` forbids syncing
 * *derived aggregates*; a stated preference is the opposite of derived.
 *
 * It also lands in the sheet, which is the right outcome: the store is meant to be
 * a human-readable mirror, and a settings row there is legible rather than hidden.
 */
data class Preference(val key: String, val enabled: Boolean, val deleted: Boolean) {
    companion object {
        const val ENTITY = "preference"
        const val FIELD_ENABLED = "enabled"

        /**
         * Whether the gamification layer runs at all.
         *
         * `PLAN.md` §5 and `CLAUDE.md` both order this: Gentle tier only, **off
         * switch first**. The switch existing before the mechanics is the whole
         * point — a layer that cannot be declined is not gentle.
         *
         * Default on, matching §5's design, but see [GAMIFICATION_DEFAULT].
         */
        const val GAMIFICATION = "gamification"

        /**
         * Gamification defaults on.
         *
         * The evidence base in §5 is thin-to-negative for the *loud* mechanics,
         * which is why only the Gentle tier ships — and what remains (a rolling
         * count of days recorded, a character whose colour warms) is judged on
         * the ostrich test rather than on engagement. Shipping it off by default
         * would make the off switch pointless and the tier untested.
         */
        const val GAMIFICATION_DEFAULT = true

        fun events(key: String, enabled: Boolean, issue: () -> Hlc): List<Event> {
            require(key.isNotBlank()) { "a preference needs a key" }
            return listOf(Event(issue(), ENTITY, key, FIELD_ENABLED, TaggedValue.Bool(enabled)))
        }

        fun from(key: EventLog.EntityKey, entity: EventLog.Entity): Preference? {
            if (key.entity != ENTITY) return null
            val enabled = (entity.fields[FIELD_ENABLED] as? TaggedValue.Bool)?.value ?: return null
            if (key.entityId.isBlank()) return null
            return Preference(key.entityId, enabled, entity.deleted)
        }
    }
}
