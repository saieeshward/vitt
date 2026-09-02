package ie.shoonya.vitt.capture

/**
 * A category with the tier that produced it.
 *
 * Always carries its provenance, because a category without one cannot be
 * debugged — see [CategorySource].
 */
data class Categorisation(val category: Category, val source: CategorySource)

/**
 * Three tiers, in order: what the user taught, then what the app shipped, then
 * ask.
 *
 * Deterministic and inspectable by design (`PLAN.md` §6): no model, no training,
 * no hosting cost, and any answer can be explained by pointing at the rule that
 * produced it. A user who disagrees can change the rule, which is not true of a
 * classifier.
 *
 * Rules match on **text, not currency**, so one rule works identically across
 * EUR, INR and AED.
 */
class Categoriser(
    /**
     * Learned rules, keyed by [MerchantName.key].
     *
     * Supplied rather than owned so the caller decides where they come from —
     * the event log in the app, a literal map in a test.
     */
    private val learned: Map<String, Category> = emptyMap(),
) {

    /**
     * Categorises a merchant string, or returns null to ask.
     *
     * Null is a real answer and the right one when nothing matches: §6's third
     * tier is *ask the user*, and a wrong guess is worse than an empty field
     * because it silently distorts a budget the user never checks.
     */
    fun categorise(merchant: String?): Categorisation? {
        val raw = merchant?.takeIf { it.isNotBlank() } ?: return null
        val key = MerchantName.key(raw) ?: return null

        // Tier 1: exact match on the learned key.
        learned[key]?.let { return Categorisation(it, CategorySource.LEARNED) }

        // Tier 1, still: a learned rule for a prefix of this merchant. `tesco`
        // taught once should catch `tesco express`, which normalises to a longer
        // key. Longest match first, so a more specific rule beats a broader one.
        learned.keys
            .filter { applies(it, key) }
            .maxByOrNull { it.length }
            ?.let { return Categorisation(learned.getValue(it), CategorySource.LEARNED) }

        // Tier 2: shipped keywords, whole words only.
        val words = key.split(' ')
        for (word in words) {
            SEED[word]?.let { return Categorisation(it, CategorySource.SEED) }
        }
        // Then a substring pass, which catches `amazonin` and `ubereats` where
        // the acquirer ran two words together.
        for ((needle, category) in SEED) {
            // Two- and three-letter keywords are too short to match as
            // substrings — `ola` inside `chocolate` would be a Transport charge.
            if (needle.length >= 4 && key.contains(needle)) {
                return Categorisation(category, CategorySource.SEED)
            }
        }
        return null
    }

    companion object {
        /**
         * Shipped keyword → category.
         *
         * Chosen for Ireland and India, the two markets this app is actually for.
         * Assigned **by purpose**: a supermarket is Groceries even though it sells
         * electronics, because that is what the money was usually for.
         *
         * Small on purpose. Every entry is a guess made on the user's behalf, and
         * the third tier — asking, then remembering — scales better than a list
         * maintained by one person.
         */
        val SEED: Map<String, Category> = buildMap {
            listOf(
                "tesco", "lidl", "aldi", "dunnes", "supervalu", "spar", "centra",
                "marks", "waitrose", "bigbasket", "zepto", "blinkit", "dmart",
                "reliancefresh", "grofers", "instamart",
            ).forEach { put(it, Category.GROCERIES) }

            listOf(
                "starbucks", "costa", "insomnia", "cafe", "coffee", "restaurant",
                "deliveroo", "justeat", "ubereats", "swiggy", "zomato", "dominos",
                "mcdonalds", "burger", "pizza", "kfc", "subway", "chipotle",
                "bakery", "eatery", "kitchen", "biryani",
            ).forEach { put(it, Category.DINING) }

            listOf(
                "irishrail", "dublinbus", "luas", "leapcard", "taxi", "freenow",
                "uber", "bolt", "ola", "rapido", "metro", "irctc", "petrol",
                "circlek", "applegreen", "shell", "topaz", "parking", "toll",
            ).forEach { put(it, Category.TRANSPORT) }

            listOf(
                "rent", "mortgage", "landlord", "letting", "management",
            ).forEach { put(it, Category.HOUSING) }

            listOf(
                "electricity", "electric", "esb", "bordgais", "gas", "water",
                "broadband", "virginmedia", "eir", "vodafone", "three", "airtel",
                "jio", "bsnl", "tneb", "council", "bin", "waste",
            ).forEach { put(it, Category.UTILITIES) }

            listOf(
                "pharmacy", "boots", "hickeys", "apollo", "medplus", "doctor",
                "clinic", "hospital", "dental", "dentist", "optician", "vhi",
                "laya", "irishlife",
            ).forEach { put(it, Category.HEALTH) }

            listOf(
                "amazon", "flipkart", "myntra", "ikea", "argos", "penneys",
                "primark", "zara", "hm", "decathlon", "currys", "harveynorman",
                "meesho", "ajio", "nykaa",
            ).forEach { put(it, Category.SHOPPING) }

            listOf(
                "cinema", "odeon", "omniplex", "pvr", "inox", "bookmyshow",
                "ticketmaster", "theatre", "concert", "steam", "playstation",
                "nintendo", "xbox",
            ).forEach { put(it, Category.ENTERTAINMENT) }

            listOf(
                "ryanair", "aerlingus", "airindia", "indigo", "vistara", "emirates",
                "booking", "airbnb", "hotel", "hostel", "expedia", "trivago",
                "makemytrip", "goibibo", "airport",
            ).forEach { put(it, Category.TRAVEL) }

            listOf(
                "netflix", "spotify", "youtube", "icloud", "googleone", "dropbox",
                "adobe", "notion", "prime", "hotstar", "disney", "audible",
                "patreon", "substack",
            ).forEach { put(it, Category.SUBSCRIPTIONS) }

            listOf(
                "salary", "payroll", "wages", "dividend", "interest", "refund",
                "reimbursement", "consulting", "invoice",
            ).forEach { put(it, Category.INCOME) }
        }

        /** A categoriser with no learned rules — seeds only. */
        fun seedsOnly(): Categoriser = Categoriser()

        /**
         * Whether a rule keyed on [ruleKey] governs the merchant keyed [key].
         *
         * The single definition of "the same merchant", so the categoriser and
         * anything asking *which past entries would this rule reach* cannot
         * drift apart — they did once, with one using prefixes and the other
         * exact equality, so a rule taught at `TESCO DUBLIN` silently failed to
         * offer to fix `TESCO NAAS`.
         *
         * Either direction counts: the brand is at the front of both, so a rule
         * on `tesco` reaches `tesco naas` and a rule on `tesco naas` reaches
         * `tesco`.
         */
        fun applies(ruleKey: String, key: String): Boolean {
            if (ruleKey.isEmpty() || key.isEmpty()) return false
            return ruleKey == key || key.startsWith("$ruleKey ") || ruleKey.startsWith("$key ")
        }
    }
}
