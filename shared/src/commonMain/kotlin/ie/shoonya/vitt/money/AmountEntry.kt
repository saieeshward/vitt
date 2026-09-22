package ie.shoonya.vitt.money

/**
 * The state behind the amount keypad.
 *
 * Entry is whole units first, with a decimal key: typing 2-5 means 25.00, and
 * 2-5-.-5 means 25.50. This is how every consumer money app works (Revolut,
 * Monzo, Wise, Venmo, Cash App, Splitwise), and it is how a person says a
 * price aloud. The first version filled the minor units first, the way a card
 * terminal does, so 2-5 meant 0.25 and every whole amount needed two extra
 * zeros; the first person to use it on a phone tripped over that on their
 * first entry.
 *
 * The decimal key is drawn, not typed, so the field never asks for a system
 * keyboard, which is Compose Multiplatform's weakest area on iOS. Fraction
 * digits are capped at the currency's exponent and a second decimal point is
 * refused, so there is no way to type an amount that does not exist.
 *
 * Held as the typed text rather than a number so that "25." and "25" remain
 * distinguishable while editing.
 */
data class AmountEntry(
    /** Exactly what has been typed: digits with at most one [POINT]. */
    val text: String = "",
    val currency: Currency = Currency.EUR,
) {
    /** Nothing typed: the keypad shows the faint placeholder. */
    val isEmpty: Boolean get() = text.isEmpty()

    /**
     * Something worth saving. Distinct from [isEmpty] because "0" and "0." are
     * typed on the way to "0.50", so the text is not empty while the value is.
     * Every Save gate checks this, never [isEmpty]: a zero row in an
     * append-only sheet can never be taken back.
     */
    val hasValue: Boolean get() = money.minor > 0L

    private val whole: String get() = text.substringBefore(POINT)
    private val fraction: String get() = if (POINT in text) text.substringAfter(POINT) else ""

    /** The value as entered so far. Zero while empty, so callers need no null check. */
    val money: Money
        get() {
            val exp = currency.exponent
            val minor = (whole.ifEmpty { "0" } + fraction.padEnd(exp, '0')).toLong()
            return Money(minor, currency)
        }

    /**
     * Appends a digit or the decimal point, ignoring input that would not fit.
     *
     * Silently ignoring is deliberate: an error state for "too many digits" is
     * noise, and the cap is far above any plausible personal transaction.
     */
    fun press(key: Char): AmountEntry {
        if (key == POINT) return point()
        require(key.isDigit()) { "not a digit: $key" }
        if (POINT in text) {
            if (fraction.length >= currency.exponent) return this
            return copy(text = text + key)
        }
        // "0" then "5" is 5, not 05: a lone leading zero is a placeholder.
        if (text == "0") return copy(text = key.toString())
        if (whole.length >= MAX_WHOLE_DIGITS) return this
        return copy(text = text + key)
    }

    private fun point(): AmountEntry {
        if (currency.exponent == 0 || POINT in text) return this
        return copy(text = whole.ifEmpty { "0" } + POINT)
    }

    fun backspace(): AmountEntry =
        if (text.isEmpty()) this else copy(text = text.dropLast(1))

    fun clear(): AmountEntry = copy(text = "")

    /**
     * Switching currency keeps the figure and drops any fraction digits the new
     * currency cannot hold: 25.50 becomes 25 in yen, not 2,550.
     */
    fun withCurrency(newCurrency: Currency): AmountEntry {
        if (newCurrency == currency) return this
        val kept = when {
            newCurrency.exponent == 0 -> whole
            POINT in text -> whole + POINT + fraction.take(newCurrency.exponent)
            else -> text
        }
        // "0.50" into yen leaves a bare "0", which would show as a solid "¥0"
        // that looks like an amount. Back to the placeholder instead.
        if (kept.trimEnd(POINT).trimStart('0').isEmpty()) return copy(currency = newCurrency, text = "")
        return copy(currency = newCurrency, text = kept)
    }

    /**
     * What the user sees while typing: the symbol, the grouped whole part, and
     * the fraction exactly as far as it has been typed, so `25.` shows as
     * `€25.` and the person can see where they are. Empty shows the full-form
     * zero, `€0.00`, as a placeholder.
     *
     * @param full pad the fraction to the currency's precision, for a stored
     * amount rather than one being typed: 25 shows as `€25.00`.
     */
    fun display(full: Boolean = false): String {
        val exp = currency.exponent
        if (text.isEmpty()) return format(currency, "0", "0".repeat(exp), exp > 0)
        val showPoint = POINT in text || (full && exp > 0)
        val frac = if (full) fraction.padEnd(exp, '0') else fraction
        return format(currency, whole.ifEmpty { "0" }, frac, showPoint)
    }

    companion object {
        const val POINT = '.'

        /**
         * Seeds the keypad with an amount that came from somewhere else: a
         * shared bank alert, a Shortcut, a widget.
         *
         * The inverse of [money], so what the keypad shows is exactly what the
         * parse found. Always the magnitude: the sign belongs to the
         * expense/income toggle, and a minus in the keypad is a digit the user
         * never typed and cannot delete.
         */
        fun of(amount: Money): AmountEntry {
            val exp = amount.currency.exponent
            val digits = amount.abs().minor.toString().padStart(exp + 1, '0')
            val text = if (exp == 0) {
                digits
            } else {
                digits.dropLast(exp) + POINT + digits.takeLast(exp)
            }
            return AmountEntry(text = text, currency = amount.currency)
        }

        /** Whole-unit digits allowed; twelve is far above any personal amount. */
        const val MAX_WHOLE_DIGITS = 12

        /**
         * The keypad holds a magnitude; the sign belongs to the transaction.
         *
         * `ofMagnitude(-12.50).money` returns +12.50, and an edit screen doing
         * `ofMagnitude(txn.amount)` then `save(entry.money)` must not flip an
         * expense to income.
         */
        fun ofMagnitude(money: Money): AmountEntry {
            val abs = if (money.minor < 0) -money.minor else money.minor
            if (abs == 0L) return AmountEntry(currency = money.currency)
            // Kept at full precision, "500.50" not "500.5": that is what a
            // person would have typed to reach it, so the keypad shows the
            // stored figure as stored and the first backspace removes the
            // trailing zero rather than a digit that was never there.
            val exp = money.currency.exponent
            val digits = abs.toString().padStart(exp + 1, '0')
            val whole = digits.dropLast(exp)
            val text = if (exp == 0) whole else whole + POINT + digits.takeLast(exp)
            return AmountEntry(text, money.currency)
        }

        /** A stored amount at full precision, e.g. `€12.50`; what [Money.display] uses. */
        fun formatMinor(minor: Long, currency: Currency): String =
            ofMagnitude(Money(minor, currency)).display(full = true)

        private fun format(currency: Currency, whole: String, frac: String, point: Boolean): String =
            isolate(currency.symbol) + group(whole, currency) + (if (point) POINT + frac else "")

        /**
         * Fences a right-to-left currency symbol off from the text around it.
         *
         * AED's symbol is Arabic, and Unicode decides a line's direction from its
         * first strong character — so an amount starting with it turned the whole
         * *line* right-to-left. A cross-currency transfer rendered backwards: the
         * string was "AED 100.00 -> JPY 709,420" and the screen showed the JPY
         * amount first, so the user read the transfer as having gone the other
         * way, on the one screen that records an exchange rate.
         *
         * Wrapping the symbol in FIRST STRONG ISOLATE / POP DIRECTIONAL ISOLATE
         * lets it render right-to-left inside its own island while counting as
         * neutral outside it, which leaves the surrounding line to the digits
         * and therefore left-to-right.
         *
         * Applied only to a symbol that actually contains a strong right-to-left
         * character, so every other currency's string is unchanged to the byte.
         * `TextStyle.textDirection` was the first attempt and did not move the
         * rendering; this works because it fixes the text rather than asking the
         * layout to override it.
         *
         * Display only. The CSV export and the sheet write `toPlainString()`,
         * which carries no symbol at all, so no invisible character can reach a
         * file.
         */
        private fun isolate(symbol: String): String =
            if (symbol.any { it.isStrongRtl() }) "⁨" + symbol + "⁩" else symbol

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
    }
}
