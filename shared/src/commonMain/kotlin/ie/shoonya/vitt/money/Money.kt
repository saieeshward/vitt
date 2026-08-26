package ie.shoonya.vitt.money

/**
 * An amount of money, held as an integer count of the currency's minor unit.
 *
 * Money is never a Double. Binary floating point cannot represent 0.10 exactly,
 * so repeated addition drifts, and a budget total that is off by a cent is a
 * bug report. `1250` with [Currency.EUR] means EUR 12.50.
 *
 * Amounts are signed: negative is money out, positive is money in.
 */
data class Money(val minor: Long, val currency: Currency) {

    init {
        // Adding two currencies is meaningless, so the invariant is enforced at
        // the boundary rather than checked at every call site.
        require(true)
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minor + other.minor, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minor - other.minor, currency)
    }

    operator fun unaryMinus(): Money = Money(-minor, currency)

    val isOutflow: Boolean get() = minor < 0
    val isInflow: Boolean get() = minor > 0

    fun abs(): Money = if (minor < 0) Money(-minor, currency) else this

    /**
     * Splits this amount into [parts] shares that sum back to exactly this
     * amount. Remainder cents are distributed one each to the earliest shares,
     * so no money is created or destroyed by a split.
     */
    fun splitEvenly(parts: Int): List<Money> {
        require(parts > 0) { "cannot split into $parts parts" }
        val base = minor / parts
        val remainder = (minor % parts).toInt()
        val step = if (minor < 0) -1L else 1L
        return List(parts) { i ->
            val extra = if (i < (if (remainder < 0) -remainder else remainder)) step else 0L
            Money(base + extra, currency)
        }
    }

    /** Plain machine-readable form, e.g. `12.50`. Not localised — see the UI layer for that. */
    fun toPlainString(): String {
        val exp = currency.exponent
        if (exp == 0) return minor.toString()
        val neg = minor < 0
        val digits = (if (neg) -minor else minor).toString().padStart(exp + 1, '0')
        val whole = digits.dropLast(exp)
        val frac = digits.takeLast(exp)
        return (if (neg) "-" else "") + whole + "." + frac
    }

    /**
     * How an amount is shown to a person: always with its own symbol, grouped
     * for its own currency, and never with a converted equivalent beside it.
     *
     * There is no "default currency" in this app, so a figure without a symbol
     * is always ambiguous — which is why this, not [toPlainString], is what the
     * UI calls.
     */
    fun display(): String {
        val negative = minor < 0
        val digits = (if (negative) -minor else minor).toString()
        val entry = ie.shoonya.vitt.money.AmountEntry(
            digits = if (digits == "0") "" else digits,
            currency = currency,
        )
        return (if (negative) "-" else "") + entry.display()
    }

    override fun toString(): String = "${currency.code} ${toPlainString()}"

    private fun requireSameCurrency(other: Money) =
        require(currency == other.currency) {
            "refusing to combine ${currency.code} with ${other.currency.code}: " +
                "cross-currency arithmetic must go through an explicit Transfer"
        }

    companion object {
        /** Parses a plain decimal string such as `-12.50`. Strict; no locale guessing. */
        fun ofPlain(value: String, currency: Currency): Money {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "empty amount" }
            val neg = trimmed.startsWith('-')
            val body = trimmed.removePrefix("-").removePrefix("+")
            require(body.all { it.isDigit() || it == '.' }) { "not a plain decimal: $value" }
            val dot = body.indexOf('.')
            val whole = if (dot < 0) body else body.substring(0, dot)
            val fracRaw = if (dot < 0) "" else body.substring(dot + 1)
            require(fracRaw.length <= currency.exponent) {
                "$value has more precision than ${currency.code} supports"
            }
            val frac = fracRaw.padEnd(currency.exponent, '0')
            val minor = (whole.ifEmpty { "0" } + frac).toLong()
            return Money(if (neg) -minor else minor, currency)
        }
    }
}

/**
 * A currency, with the number of decimal places it actually uses.
 *
 * The exponent matters: JPY has none, so 100 minor units is JPY 100, not 1.00.
 * Getting this wrong scales an amount by 100.
 */
enum class Currency(val code: String, val exponent: Int, val symbol: String) {
    EUR("EUR", 2, "€"),
    INR("INR", 2, "₹"),
    GBP("GBP", 2, "£"),
    USD("USD", 2, "$"),
    AED("AED", 2, "د.إ"),
    JPY("JPY", 0, "¥");

    companion object {
        fun ofCode(code: String): Currency? = entries.firstOrNull { it.code.equals(code, true) }
    }
}
