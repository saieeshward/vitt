package ie.shoonya.vitt.capture

/**
 * Turns a card-network merchant string into something a person recognises.
 *
 * `TESCO STORES 3421 DUBLIN IE` → `Tesco`. This matters twice: it is what the
 * activity list shows, and per `PLAN.md` §6 the same normalisation is the key
 * that learned category rules are stored under — so a rule taught at one Tesco
 * applies at every other one.
 *
 * Deliberately conservative. Over-stripping merges two genuinely different
 * merchants under one rule, which mis-categorises silently; under-stripping only
 * means a rule has to be taught twice. Given the choice, it under-strips.
 */
object MerchantName {

    /**
     * The display form: normalised, trimmed of acquirer noise, title-cased.
     *
     * Returns null when nothing recognisable survives — a string of terminal ids
     * and country codes is worse than no merchant at all, because it looks like
     * data.
     */
    fun clean(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // A merchant a person typed is already the name they want. Normalising it
        // does damage rather than good: "Taxi to airport" lost its middle word to
        // the stop-word list, and "Dinner with Anya" came back title-cased as
        // "Dinner With Anya". Only machine text gets cleaned.
        if (!looksMachineWritten(trimmed)) return trimmed
        val words = tokenise(trimmed)
        if (words.isEmpty()) return null
        // Keep at most three words. Acquirer strings run "BRAND … LOCATION", so
        // the brand is at the front and the tail is almost always noise.
        return words.take(3).joinToString(" ") { titleCase(it) }
    }

    /**
     * Whether a merchant string came from a card network rather than a person.
     *
     * Shouting, digits, or an aggregator's `*` — the three things acquirer
     * strings do and typed names do not. Conservative on purpose: misjudging
     * machine text as typed leaves a terminal id on screen, which is ugly;
     * misjudging typed text as machine deletes words the user chose, which is
     * worse.
     */
    private fun looksMachineWritten(raw: String): Boolean {
        val letters = raw.filter { it.isLetter() }
        val shouting = letters.isNotEmpty() && letters.none { it.isLowerCase() }
        return shouting || raw.any { it.isDigit() } || raw.contains('*')
    }

    /**
     * The matching key for learned rules: lowercase, punctuation-free, no noise.
     *
     * A separate function from [clean] so that display can change — nicer casing,
     * a longer name — without invalidating every rule the user has taught.
     */
    fun key(raw: String): String? {
        val words = tokenise(raw)
        if (words.isEmpty()) return null
        return words.take(3).joinToString(" ")
    }

    private fun tokenise(raw: String): List<String> {
        val normalised = raw
            // Zero-width characters travel in copied bank text and would make two
            // visually identical merchants hash differently.
            .filter { it != '​' && it != '‌' && it != '‍' && it != '﻿' }
            .lowercase()
            // Separators the networks use interchangeably, plus the `*` that
            // aggregators prefix (`SQ *COFFEE`, `PAYPAL *SOMEONE`).
            .map { if (it.isLetterOrDigit() || it == '@') it else ' ' }
            .joinToString("")

        val words = normalised.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
        val kept = mutableListOf<String>()
        for (word in words) {
            // A city is only noise once a brand is already in hand. Acquirer
            // strings run brand-first, so a leading city name is the brand —
            // `KILKENNY DESIGN` and `CORK CHEESE` are real merchants, and
            // stripping position one turned the first into "Design".
            // Length-guarded: a bare country code (`ie`, `in`, `uk`) leading the
            // string is not a brand, and exempting it turned `POS 4419 XXXX IE`
            // into the merchant "Ie".
            val cityAsBrand = kept.isEmpty() && word.length > 2 && word in CITIES
            if (cityAsBrand) {
                kept += word
                continue
            }
            if (!isNoise(word)) kept += word
        }
        return kept
    }

    private fun isNoise(word: String): Boolean {
        // Short alphanumeric brands are checked first, because every rule below
        // would otherwise eat them.
        if (word in SHORT_BRANDS) return false
        if (word in ACQUIRER_NOISE) return true
        if (word in CITIES) return true
        // Pure digits: store numbers, terminal ids, reference numbers.
        if (word.all { it.isDigit() }) return true
        // Two-letter tokens are country codes far more often than brands.
        if (word.length == 2) return true
        // Mixed alphanumeric reads as a terminal or store id — `t4419`,
        // `str0031`. Length-guarded, because `o2` and `3m` are real names and an
        // unguarded rule deleted them.
        if (word.length >= 4 && word.any { it.isDigit() } && word.any { it.isLetter() }) return true
        return false
    }

    private fun titleCase(word: String): String =
        if (word.length <= 1) word.uppercase()
        else word[0].uppercase() + word.substring(1)

    /** Words that carry no identity. */
    private val ACQUIRER_NOISE = setOf(
        "pos", "posted", "purchase", "payment", "card", "visa", "mastercard",
        "debit", "credit", "contactless", "chip", "pin", "auth", "authorisation",
        "authorization", "txn", "transaction", "ref", "reference", "terminal",
        "store", "stores", "shop", "ltd", "limited", "plc", "inc", "llc", "co",
        "the", "and", "of", "at", "on", "via", "from", "to",
        "xxxx", "x", "sq", "paypal", "sumup", "stripe", "izettle", "revolut",
    )

    /**
     * Alphanumeric or two-letter tokens that really are brand names.
     *
     * Small and explicit, because the id-stripping rules are otherwise right far
     * more often than they are wrong, and a general exception would let every
     * terminal id through.
     */
    private val SHORT_BRANDS = setOf("o2", "3m", "hm", "b2", "h2", "m1", "ba", "kfc", "pvr")

    /**
     * Cities in the two markets this app is for, which §6 says to strip:
     * `TESCO STORES 3421 DUBLIN IE` should become `Tesco`, not `Tesco Dublin`.
     *
     * A list rather than a gazetteer, so it stays reviewable. The known risk is a
     * merchant genuinely named after a city — `Cork Cheese Co` loses its first
     * word. That is recoverable in one gesture, because teaching a rule keys on
     * whatever survives, and it is rarer than the branch-name noise this removes.
     */
    private val CITIES = setOf(
        // Ireland
        "dublin", "cork", "galway", "limerick", "waterford", "kilkenny", "sligo",
        "drogheda", "dundalk", "bray", "navan", "swords", "tallaght", "blanchardstown",
        "ie", "ireland", "eire",
        // India
        "bangalore", "bengaluru", "mumbai", "delhi", "newdelhi", "chennai",
        "hyderabad", "pune", "kolkata", "ahmedabad", "kochi", "cochin", "jaipur",
        "gurgaon", "gurugram", "noida", "in", "india",
        // Other places these accounts see
        "london", "amsterdam", "paris", "berlin", "dubai", "uk", "nl", "de", "ae",
    )
}
