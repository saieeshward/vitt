package ie.shoonya.vitt.capture

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money

enum class Direction { OUTFLOW, INFLOW, UNKNOWN }

/**
 * How much the parser trusts its own output.
 *
 * [HIGH] may be committed after a single confirming tap. [MEDIUM] pre-fills but
 * highlights the uncertain field. [LOW] goes to a review queue showing the raw
 * text, because a wrong amount silently corrupts a ledger and a wrong *sign*
 * corrupts it twice over.
 */
enum class Confidence { HIGH, MEDIUM, LOW }

data class ParsedTransaction(
    val amount: Money?,
    val direction: Direction,
    val merchant: String?,
    val confidence: Confidence,
    val warnings: List<String>,
    val raw: String,
) {
    /**
     * Whether this can be committed without the user resolving something.
     *
     * A parse whose direction is unknown is deliberately NOT usable: the sign is
     * the field that inverts money, and guessing it turns a EUR 40 refund into a
     * EUR 40 expense — an EUR 80 error the user will not notice for weeks.
     */
    val isUsable: Boolean get() = amount != null && direction != Direction.UNKNOWN

    /** The magnitude, available even when the direction could not be determined. */
    val magnitude: Money? get() = amount?.abs()
}

/**
 * Extracts a transaction from a single piece of text the user handed over
 * deliberately — a shared bank notification, a pasted SMS, a clipboard snippet.
 *
 * This never reads notifications itself. Google Play's Sensitive Permissions
 * policy forbids deriving SMS-attributed data by alternative means, and
 * notification listening for bank alerts is the named target of that clause.
 * Text arrives here only because someone chose to send it.
 *
 * Deliberately conservative: it would rather return [Confidence.LOW] and ask
 * than guess. Guessing wrong about a sign turns a EUR 40 refund into a EUR 40
 * expense, an EUR 80 error that the user will not notice for weeks.
 */
object AmountParser {

    fun parse(rawInput: String, defaultCurrency: Currency? = null): ParsedTransaction {
        val warnings = mutableListOf<String>()
        val raw = rawInput
        val text = normalise(rawInput)

        if (text.isBlank()) {
            return ParsedTransaction(null, Direction.UNKNOWN, null, Confidence.LOW,
                listOf("empty text"), raw)
        }

        val truncated = looksTruncated(rawInput)
        if (truncated) warnings += "text appears truncated"

        val currency = detectCurrency(text) ?: defaultCurrency
        if (currency == null) warnings += "no currency found"

        val amountMatch = currency?.let { findAmount(text, it, warnings) }
        val direction = detectDirection(text)
        if (direction == Direction.UNKNOWN) warnings += "direction unclear"

        val merchant = detectMerchant(text, truncated, warnings)

        val signed = amountMatch?.let { m ->
            when (direction) {
                Direction.OUTFLOW -> Money(-m.abs, currency)
                Direction.INFLOW -> Money(m.abs, currency)
                // Magnitude only. The sign is withheld rather than guessed:
                // isUsable is false for UNKNOWN, so this cannot be committed
                // without the user choosing a direction.
                Direction.UNKNOWN -> Money(m.abs, currency)
            }
        }

        val confidence = when {
            signed == null -> Confidence.LOW
            direction == Direction.UNKNOWN -> Confidence.LOW
            amountMatch.ambiguousSeparator -> Confidence.LOW
            truncated && merchant == null -> Confidence.MEDIUM
            warnings.isEmpty() -> Confidence.HIGH
            else -> Confidence.MEDIUM
        }

        return ParsedTransaction(signed, direction, merchant, confidence, warnings, raw)
    }

    // ---- normalisation -------------------------------------------------------

    /**
     * Collapses the text into a comparable form. Bank messages arrive with
     * non-breaking spaces around currency symbols, zero-width joiners from
     * copy-paste, and inconsistent casing.
     */
    internal fun normalise(input: String): String = input
        .replace(' ', ' ')   // non-breaking space, common before symbols
        .replace(' ', ' ')   // narrow no-break space
        .replace("​", "")    // zero-width space
        .replace("‍", "")    // zero-width joiner
        .replace("﻿", "")    // BOM
        .replace(Regex("\\s+"), " ")
        .trim()

    private val TRUNCATION_MARKERS = listOf("…", "...", "…")

    private fun looksTruncated(input: String): Boolean {
        val t = input.trimEnd()
        return TRUNCATION_MARKERS.any { t.endsWith(it) }
    }

    // ---- currency ------------------------------------------------------------

    /**
     * Symbol and code detection. Indian messages are the awkward case: the same
     * bank will use "Rs.", "Rs", "INR" and "₹" interchangeably, and sometimes a
     * trailing "/-" with no marker at all.
     */
    private val CURRENCY_PATTERNS: List<Pair<Regex, Currency>> = listOf(
        // '500/-' carries no symbol but is unambiguously rupees in Indian usage,
        // so the suffix itself has to count as a currency marker.
        Regex("₹|\\bRs\\.?\\b|\\bINR\\b|\\d\\s*/-", RegexOption.IGNORE_CASE) to Currency.INR,
        Regex("€|\\bEUR\\b", RegexOption.IGNORE_CASE) to Currency.EUR,
        Regex("£|\\bGBP\\b", RegexOption.IGNORE_CASE) to Currency.GBP,
        Regex("\\bAED\\b|د\\.إ", RegexOption.IGNORE_CASE) to Currency.AED,
        Regex("¥|\\bJPY\\b", RegexOption.IGNORE_CASE) to Currency.JPY,
        // '$' last: it is ambiguous across USD/CAD/AUD/SGD, so an explicit code wins.
        Regex("\\bUSD\\b|\\$", RegexOption.IGNORE_CASE) to Currency.USD,
    )

    /** Explicit ISO codes, checked before any symbol or abbreviation. */
    private val ISO_CODES = Regex("\\b(EUR|INR|GBP|USD|AED|JPY)\\b", RegexOption.IGNORE_CASE)

    internal fun detectCurrency(text: String): Currency? {
        // An explicit code wins outright. Otherwise "GBP 20.00 paid to RS McColl"
        // resolves to rupees, because the INR pattern matches "RS" anywhere and
        // is checked first.
        ISO_CODES.find(text)?.let { return Currency.ofCode(it.value.uppercase()) }
        return CURRENCY_PATTERNS.firstOrNull { (re, _) -> re.containsMatchIn(text) }?.second
    }

    // ---- direction -----------------------------------------------------------

    private val OUTFLOW_WORDS = Regex(
        "\\b(debited|debit|spent|paid|payment|withdrawn|withdrawal|purchase|" +
            "sent|transferred to|charged|deducted|dr)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val INFLOW_WORDS = Regex(
        "\\b(credited|credit|received|refund|refunded|reversed|reversal|" +
            "deposited|salary|cashback|added|cr)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Direction is the field most often got wrong, and the only one where being
     * wrong flips a sign. When both families of words appear — "refund credited
     * to your card ending 1234 for a purchase" — we refuse to choose.
     */
    internal fun detectDirection(text: String): Direction {
        val out = OUTFLOW_WORDS.containsMatchIn(text)
        val inn = INFLOW_WORDS.containsMatchIn(text)
        return when {
            out && !inn -> Direction.OUTFLOW
            inn && !out -> Direction.INFLOW
            else -> Direction.UNKNOWN
        }
    }

    // ---- merchant ------------------------------------------------------------

    private val MERCHANT_HINT = Regex(
        "(?:\\bat\\b|\\bto\\b|\\bfrom\\b|\\bVPA\\b|\\btowards\\b)\\s+([A-Za-z0-9@._*\\- ]{2,40})",
        RegexOption.IGNORE_CASE,
    )

    private val MERCHANT_NOISE = Regex(
        "\\b(POS|TXN|TERMINAL|REF|UPI|CARD|XX+\\d*|\\d{4,})\\b",
        RegexOption.IGNORE_CASE,
    )

    internal fun detectMerchant(text: String, truncated: Boolean, warnings: MutableList<String>): String? {
        val captured = MERCHANT_HINT.find(text)?.groupValues?.getOrNull(1) ?: return null
        val cleaned = captured
            .replace(MERCHANT_NOISE, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ',', '-', '*')
        if (cleaned.length < 2) return null
        if (truncated) warnings += "merchant may be cut off"
        return cleaned.split(' ').joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() }) w
            else w.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    // ---- amount --------------------------------------------------------------

    internal data class AmountMatch(val abs: Long, val ambiguousSeparator: Boolean)

    /**
     * Numbers with optional grouping. Accepts Western grouping (1,234,567) and
     * Indian grouping (12,34,567) alike; which one it was gets decided later.
     */
    // The grouped alternative requires at least one group (`+`, not `*`).
    // With `*` it matched the bare first three digits of any ungrouped number
    // and won, because Kotlin regex alternation is leftmost-first rather than
    // longest-match: "1500" matched as "150", silently reporting EUR 1500 as
    // EUR 150.00 at HIGH confidence.
    private val NUMBER = Regex("\\d{1,3}(?:[.,]\\d{2,3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?")

    private fun findAmount(text: String, currency: Currency, warnings: MutableList<String>): AmountMatch? {
        val candidates = NUMBER.findAll(text).map { it.value }.toList()
        if (candidates.isEmpty()) return null

        // Prefer a number adjacent to the currency marker; "Rs.500 at shop 1234"
        // must not resolve to 1234. Falls back to the longest candidate.
        val nearCurrency = Regex(
            "(?:₹|Rs\\.?|INR|€|EUR|£|GBP|\\$|USD|AED)\\s*(${NUMBER.pattern})" +
                "|(${NUMBER.pattern})\\s*(?:₹|Rs\\.?|INR|€|EUR|£|GBP|/-)",
            RegexOption.IGNORE_CASE,
        ).find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }

        val chosen = nearCurrency ?: candidates.maxByOrNull { it.length }!!
        if (nearCurrency == null && candidates.size > 1) {
            warnings += "several numbers present; picked the longest"
        }
        return interpretNumber(chosen, currency, warnings)
    }

    /**
     * Resolves separator meaning. This is where a naive parser loses money.
     *
     * `1.234` is one thousand two hundred and thirty four in Germany and one
     * point two three four in Ireland. There is no way to tell from the digits
     * alone, so:
     *
     *  - both separators present  -> the rightmost is the decimal point
     *  - one separator, 2 digits after -> decimal (`12,50` is twelve fifty)
     *  - one separator, 3 digits after -> grouping, but flagged ambiguous
     *  - no separator -> whole units
     */
    internal fun interpretNumber(token: String, currency: Currency, warnings: MutableList<String>): AmountMatch? {
        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')
        var ambiguous = false

        val plain: String = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimalAt = maxOf(lastDot, lastComma)
                val groupSep = if (decimalAt == lastDot) ',' else '.'
                token.filter { it != groupSep }
                    .replace(if (decimalAt == lastDot) '.' else ',', '.')
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val at = maxOf(lastDot, lastComma)
                val after = token.length - at - 1
                val sep = token[at]
                when {
                    after == 2 -> token.replaceRange(at, at + 1, ".").filter { it.isDigit() || it == '.' }
                    after == 3 -> {
                        // Could be grouping (1,234) or three-decimal precision.
                        // Grouping is far more common in bank text, but say so.
                        ambiguous = true
                        warnings += "'$token' is ambiguous: read as grouping, not decimals"
                        token.filter { it.isDigit() }
                    }
                    else -> {
                        ambiguous = true
                        warnings += "unexpected digit grouping in '$token'"
                        token.replace(sep, '.')
                    }
                }
            }
            else -> token
        }

        val dot = plain.indexOf('.')
        val whole = if (dot < 0) plain else plain.substring(0, dot)
        val frac = if (dot < 0) "" else plain.substring(dot + 1)
        if (whole.isEmpty() || !whole.all { it.isDigit() }) return null
        if (frac.length > currency.exponent) {
            warnings += "more decimal places than ${currency.code} uses"
            return null
        }
        val minorDigits = whole + frac.padEnd(currency.exponent, '0')
        val value = minorDigits.toLongOrNull() ?: return null
        return AmountMatch(value, ambiguous)
    }
}
