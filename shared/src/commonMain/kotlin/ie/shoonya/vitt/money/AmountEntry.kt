package ie.shoonya.vitt.money

/**
 * The state behind the amount keypad.
 *
 * Entry is right-to-left, the way an ATM or a card terminal works: digits fill
 * the minor units first, so typing 1-2-5-0 yields 12.50. There is deliberately
 * no decimal-point key.
 *
 * That choice does two things. It removes an entire class of user error — no
 * ambiguity about whether "12.5" meant twelve fifty or twelve and a half, and
 * no way to type two decimal points. And because the field never asks for a
 * system keyboard, it side-steps text input entirely, which is Compose
 * Multiplatform's weakest area on iOS. The interaction that matters most in
 * this app is therefore the one least exposed to the framework's soft spot.
 *
 * Held as a digit string rather than a number so that a leading zero being
 * typed, and a value of zero, remain distinguishable while editing.
 */
data class AmountEntry(
    val digits: String = "",
    val currency: Currency = Currency.EUR,
) {
    val isEmpty: Boolean get() = digits.isEmpty()

    /** The value as entered so far. Zero while empty, so callers need no null check. */
    val money: Money get() = Money(digits.ifEmpty { "0" }.toLong(), currency)

    /**
     * Appends a digit, ignoring input that would exceed [MAX_DIGITS].
     *
     * Silently ignoring is deliberate: an error state for "too many digits" is
     * noise, and the cap is far above any plausible personal transaction.
     */
    fun press(digit: Char): AmountEntry {
        require(digit.isDigit()) { "not a digit: $digit" }
        // A leading zero carries no value in right-to-left entry.
        if (digits.isEmpty() && digit == '0') return this
        if (digits.length >= MAX_DIGITS) return this
        return copy(digits = digits + digit)
    }

    fun backspace(): AmountEntry =
        if (digits.isEmpty()) this else copy(digits = digits.dropLast(1))

    fun clear(): AmountEntry = copy(digits = "")

    /** Switching currency reinterprets the same digits, since the exponent may differ. */
    fun withCurrency(newCurrency: Currency): AmountEntry {
        if (newCurrency == currency) return this
        return copy(currency = newCurrency, digits = digits.take(maxDigitsFor(newCurrency)))
    }

    /**
     * What the user sees, grouped and with the currency symbol, e.g. `€12.50`.
     * Always shows the full minor-unit precision so the field never appears to
     * change value as digits are added.
     */
    fun display(): String {
        val exp = currency.exponent
        val padded = digits.padStart(exp + 1, '0')
        val whole = padded.dropLast(exp).ifEmpty { "0" }
        val frac = if (exp == 0) "" else "." + padded.takeLast(exp)
        return currency.symbol + group(whole, currency) + frac
    }

    private fun group(whole: String, currency: Currency): String {
        if (whole.length <= 3) return whole
        return when (currency) {
            // Indian grouping is 2-2-3 from the right, not 3-3-3: 12,34,567.
            Currency.INR -> {
                val last3 = whole.takeLast(3)
                val rest = whole.dropLast(3)
                val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
                "$pairs,$last3"
            }
            else -> whole.reversed().chunked(3).joinToString(",").reversed()
        }
    }

    companion object {
        const val MAX_DIGITS = 12

        private fun maxDigitsFor(currency: Currency) = MAX_DIGITS

        fun of(money: Money): AmountEntry {
            val abs = if (money.minor < 0) -money.minor else money.minor
            return AmountEntry(if (abs == 0L) "" else abs.toString(), money.currency)
        }
    }
}
