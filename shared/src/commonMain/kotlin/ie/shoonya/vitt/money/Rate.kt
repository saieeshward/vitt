package ie.shoonya.vitt.money

/**
 * The rate at which money actually moved, held as the two amounts it moved
 * between rather than as a quotient.
 *
 * This is the product's one recorded rate (`PLAN.md` §0.6). It is never fetched,
 * never estimated, and never a market figure: it is a fact observed from a
 * transfer that happened. Splitwise Pro converts at *today's* rate and Tricount
 * at the entry-day rate, so in both the amount owed moves after the fact. Here it
 * cannot, because the rate is the two legs.
 *
 * Stored as a fraction for the same reason [Money] is an integer: a rate is
 * inherently non-terminating in binary — 1/3 of anything, most JPY pairs — and a
 * Double would make the stored rate disagree with the amounts it came from. The
 * quotient is computed only for display, and only at a requested precision.
 */
data class Rate(
    val from: Currency,
    val to: Currency,
    /** Magnitude sent, in [from]'s minor units. Always positive. */
    val sentMinor: Long,
    /** Magnitude received, in [to]'s minor units. Always positive. */
    val receivedMinor: Long,
) {
    init {
        require(sentMinor > 0) { "a rate needs a positive amount sent" }
        require(receivedMinor > 0) { "a rate needs a positive amount received" }
    }

    /**
     * Units of [to] per one unit of [from], scaled by `10^decimals`.
     *
     * Null when the result cannot be represented in a [Long] — better to show
     * nothing than a silently wrapped number. In practice this needs amounts far
     * beyond personal finance; see the overflow note on [mulDiv].
     *
     * The exponents matter in both directions. EUR→JPY and JPY→EUR differ by a
     * factor of 100 and nothing else, so dropping either exponent yields a rate
     * that looks plausible and is wrong by two orders of magnitude.
     */
    fun scaledBy(decimals: Int): Long? {
        require(decimals >= 0) { "decimals cannot be negative" }
        // rate = (receivedMinor / 10^to.exp) / (sentMinor / 10^from.exp)
        //      = receivedMinor * 10^from.exp / (sentMinor * 10^to.exp)
        // scaled by 10^decimals, and the two powers of ten partly cancel.
        val net = from.exponent + decimals - to.exponent
        return if (net >= 0) {
            mulDiv(receivedMinor, pow10(net) ?: return null, sentMinor)
        } else {
            val divisor = pow10(-net) ?: return null
            mulDiv(receivedMinor, 1, sentMinor.timesOrNull(divisor) ?: return null)
        }
    }

    /**
     * The rate as a decimal string, e.g. `90.123456`, or null if unrepresentable.
     *
     * Not localised — the UI layer decides the separator, exactly as with
     * [Money.toPlainString].
     */
    fun toPlainString(decimals: Int = DEFAULT_DECIMALS): String? {
        val scaled = scaledBy(decimals) ?: return null
        if (decimals == 0) return scaled.toString()
        val digits = scaled.toString().padStart(decimals + 1, '0')
        return digits.dropLast(decimals) + "." + digits.takeLast(decimals)
    }

    /** The same movement read backwards: units of [from] per one unit of [to]. */
    fun inverse(): Rate = Rate(from = to, to = from, sentMinor = receivedMinor, receivedMinor = sentMinor)

    companion object {
        /**
         * Six places, which holds the pairs this app cares about.
         *
         * INR→EUR sits near 0.0106, so four places would round two significant
         * figures away and make a large transfer's rate visibly disagree with its
         * own legs.
         */
        const val DEFAULT_DECIMALS = 6

        private fun pow10(n: Int): Long? {
            if (n > 18) return null
            var r = 1L
            repeat(n) { r *= 10 }
            return r
        }

        private fun Long.timesOrNull(other: Long): Long? {
            val r = this * other
            return if (other != 0L && r / other != this) null else r
        }

        /**
         * `a * b / d`, rounded half away from zero, without overflowing where it
         * can be avoided.
         *
         * Reduces both sides by their common factors before multiplying, which is
         * what keeps realistic amounts in range: the powers of ten in a rate
         * share most of their factors with the minor amounts they scale. Returns
         * null rather than a wrapped [Long] when even the reduced product will
         * not fit.
         */
        private fun mulDiv(a: Long, b: Long, d: Long): Long? {
            var x = a
            var y = b
            var z = d
            gcd(x, z).let { if (it > 1) { x /= it; z /= it } }
            gcd(y, z).let { if (it > 1) { y /= it; z /= it } }
            val product = x.timesOrNull(y) ?: return null
            // Round half away from zero; both inputs are positive here.
            val doubled = product.timesOrNull(2) ?: return product / z
            return (doubled / z + 1) / 2
        }

        private fun gcd(a: Long, b: Long): Long {
            var x = if (a < 0) -a else a
            var y = if (b < 0) -b else b
            while (y != 0L) {
                val t = x % y
                x = y
                y = t
            }
            return x
        }
    }
}
