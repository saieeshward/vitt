package ie.shoonya.vitt.model

import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue

/**
 * A preference whose value is one of several named options, not a switch.
 *
 * A sibling of [Preference] rather than a widening of it. [Preference] answers
 * yes-or-no and its field is a `Bool`, so making it hold a string would either
 * mean a nullable second field on every row or a union type read at every call
 * site. Two entities with one field each is the cheaper shape, and the event log
 * already keys on entity plus id.
 *
 * Carried in the event log for the reason [Preference] is: a choice the user
 * stated on one device coming back different on another would be the app
 * overriding a decision they already made. `PLAN.md:222` forbids syncing
 * *derived aggregates*, and a stated choice is the opposite of derived.
 *
 * **The value is not validated here.** A row naming an option this build does
 * not have is kept, not dropped: it may come from a newer version on the user's
 * other phone, and silently rewriting it to the default would lose their choice
 * permanently. Resolution to a real option happens at the point of use, which
 * falls back for display without touching the stored value.
 */
data class Choice(val key: String, val value: String, val deleted: Boolean) {
    companion object {
        const val ENTITY = "choice"
        const val FIELD_VALUE = "value"

        /** Which companion animal is drawn on the home screen. */
        const val COMPANION = "companion"

        /** Which palette the app is painted in. */
        const val THEME = "theme"

        /** Which accent marks what is live, within the chosen palette. */
        const val ACCENT = "accent"

        fun events(key: String, value: String, issue: () -> Hlc): List<Event> {
            require(key.isNotBlank()) { "a choice needs a key" }
            require(value.isNotBlank()) { "a choice needs a value" }
            return listOf(Event(issue(), ENTITY, key, FIELD_VALUE, TaggedValue.Str(value)))
        }

        fun from(key: EventLog.EntityKey, entity: EventLog.Entity): Choice? {
            if (key.entity != ENTITY) return null
            val value = (entity.fields[FIELD_VALUE] as? TaggedValue.Str)?.value ?: return null
            if (key.entityId.isBlank()) return null
            return Choice(key.entityId, value, entity.deleted)
        }
    }
}
