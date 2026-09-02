package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.MerchantName
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue

/**
 * A rule the user taught: this merchant means this category.
 *
 * Tier 1 of `PLAN.md` §6. Written whenever someone corrects a category, so the
 * correction is made once rather than every month — which is the entire value of
 * the tier, and the reason it beats a shipped keyword list that one person has to
 * maintain.
 *
 * Keyed on the normalised merchant, not the raw string, so a rule taught at
 * `TESCO STORES 3421 DUBLIN IE` also fires for `TESCO EXPRESS 88 CORK`. Matching
 * is on text and never on currency, so one rule works across every ledger.
 */
data class CategoryRule(
    /** The normalised merchant key — see [MerchantName.key]. */
    val merchantKey: String,
    val category: Category,
    val deleted: Boolean,
) {
    companion object {
        const val ENTITY = "category_rule"

        const val FIELD_MERCHANT = "merchant"
        const val FIELD_CATEGORY = "category"

        /**
         * The normalised merchant is the entity id.
         *
         * A natural key, so two devices teaching the same merchant converge on
         * one rule instead of two that disagree. There can only be one rule per
         * merchant by definition — a merchant meaning two categories is not a
         * rule, it is a coin toss.
         */
        fun idFor(merchantKey: String): String = merchantKey

        /**
         * Builds the events for a rule, or null when the merchant normalises to
         * nothing.
         *
         * Null rather than an exception: the caller is usually a category edit on
         * a transaction with no merchant at all, and that is an ordinary thing to
         * do, not an error. The category still lands on the transaction; there is
         * simply nothing to key a rule to.
         */
        fun events(rawMerchant: String, category: Category, issue: () -> Hlc): List<Event>? {
            val key = MerchantName.key(rawMerchant) ?: return null
            val id = idFor(key)
            return listOf(
                Event(issue(), ENTITY, id, FIELD_MERCHANT, TaggedValue.Str(key)),
                Event(issue(), ENTITY, id, FIELD_CATEGORY, TaggedValue.Str(category.code)),
            )
        }

        /**
         * Projects a folded entity into a rule.
         *
         * A rule naming a category this version does not know is dropped rather
         * than coerced to Miscellaneous: silently re-categorising someone's rule
         * is worse than the rule not firing, because the app would then be
         * confidently wrong on every future transaction from that merchant.
         */
        fun from(key: EventLog.EntityKey, entity: EventLog.Entity): CategoryRule? {
            if (key.entity != ENTITY) return null
            val category = (entity.fields[FIELD_CATEGORY] as? TaggedValue.Str)
                ?.value?.let { Category.ofCode(it) } ?: return null
            val merchant = (entity.fields[FIELD_MERCHANT] as? TaggedValue.Str)?.value
                ?: key.entityId
            if (merchant.isBlank()) return null
            return CategoryRule(
                merchantKey = merchant,
                category = category,
                deleted = entity.deleted,
            )
        }
    }
}
