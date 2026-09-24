package ie.shoonya.vitt.money

/**
 * Names to offer as one-tap chips when someone is setting up their accounts.
 *
 * The whole point of the first-run screen is that naming five accounts should
 * take twenty seconds of tapping rather than three minutes of typing on a phone
 * keyboard, and that only works if the names people actually use are already on
 * screen.
 *
 * These are suggestions and nothing more. A chip fills a free-text field the
 * user owns and can edit or replace immediately, no name is stored as anything
 * but the string they accepted, and the app has no relationship of any kind with
 * the institutions named. Using a bank's name to refer to that bank is what the
 * name is for.
 *
 * Every currency ends with the generic set, so a person whose bank is not listed
 * still has something to tap, and so a currency the app has never had a list for
 * degrades to something usable rather than to an empty row.
 */
object AccountSuggestions {

    /**
     * The fallbacks, appended to every currency.
     *
     * "Card" rather than "Credit card" because the chip fills a *name*, and
     * nobody calls their card "Credit card" when talking about it. The kind is a
     * separate field that first-run does not ask about.
     */
    private val generic = listOf("Current", "Savings", "Card", "Cash")

    private val byCurrency: Map<Currency, List<String>> = mapOf(
        Currency.EUR to listOf("AIB", "Bank of Ireland", "Revolut", "N26"),
        Currency.INR to listOf("HDFC", "ICICI", "SBI", "Axis", "Paytm"),
        Currency.GBP to listOf("Monzo", "Starling", "Barclays", "HSBC"),
        Currency.USD to listOf("Chase", "Bank of America", "Wells Fargo"),
        Currency.AED to listOf("Emirates NBD", "ADCB", "Mashreq"),
        Currency.JPY to listOf("MUFG", "SMBC", "Japan Post"),
    )

    /** What to offer for a currency: its own names first, then the generic ones. */
    fun forCurrency(currency: Currency): List<String> =
        (byCurrency[currency].orEmpty() + generic).distinct()

    /** The banks people in that currency usually have, for their own row. */
    fun banks(currency: Currency): List<String> = byCurrency[currency].orEmpty()

    /** Names by kind rather than by bank, offered in every currency. */
    val kinds: List<String> get() = generic
}
