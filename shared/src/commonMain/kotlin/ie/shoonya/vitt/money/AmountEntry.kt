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
        return isolate(currency.symbol) + group(whole, currency) + frac
    }

    /**
     * Fences an right-to-left currency symbol off from the text around it.
     *
     * AED's symbol is Arabic, and Unicode decides a line's direction from its
     * first strong character — so an amount starting with it turned the whole
     * *line* right-to-left. A cross-currency transfer rendered backwards: the
     * string was "AED 100.00 -> JPY 709,420" and the screen showed the JPY
     * amount first, so the user read the transfer as having gone the other way,
     * on the one screen that records an exchange rate.
     *
     * Wrapping the symbol in FIRST STRONG ISOLATE / POP DIRECTIONAL ISOLATE
     * lets it render right-to-left inside its own island while counting as
     * neutral outside it, which leaves the surrounding line to the digits and
     * therefore left-to-right.
     *
     * Applied only to a symbol that actually contains a strong right-to-left
     * character, so every other currency's string is unchanged to the byte.
     * `TextStyle.textDirection` was the first attempt and did not move the
     * rendering; this works because it fixes the text rather than asking the
     * layout to override it.
     *
     * Display only. The CSV export and the sheet write `toPlainString()`, which
     * carries no symbol at all, so no invisible character can reach a file.
     */
    private fun isolate(symbol: String): String =
        if (symbol.any { it.isStrongRtl() }) "\u2068" + symbol + "\u2069" else symbol

    /** Arabic, Hebrew, Syriac, Thaana and the Arabic presentation forms. */
    private fun Char.isStrongRtl(): Boolean = code in 0x0590..0x08FF ||
        code in 0xFB1D..0xFDFF || code in 0xFE70..0xFEFF

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

        /**
         * The keypad holds a magnitude; the sign belongs to the transaction.
         *
         * Named for what it does. The previous name implied a round trip it
         * does not perform — `of(-12.50).money` returns +12.50 — so an edit
         * screen doing `of(txn.amount)` then `save(entry.money)` would have
         * flipped every expense to income.
         */
        fun ofMagnitude(money: Money): AmountEntry {
            val abs = if (money.minor < 0) -money.minor else money.minor
            return AmountEntry(if (abs == 0L) "" else abs.toString(), money.currency)
        }
    }
}
