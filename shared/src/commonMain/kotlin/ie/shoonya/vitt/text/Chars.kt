package ie.shoonya.vitt.text

/**
 * The first [n] UTF-16 units, never cutting a surrogate pair in half.
 *
 * `take(n)` on a string ending in an emoji can keep the high surrogate and
 * drop the low one; the lone surrogate then survives trim(), reaches the
 * event log, and comes out of the sheet and the CSV as U+FFFD. Used wherever
 * user text is capped.
 */
fun String.takeChars(n: Int): String {
    if (length <= n) return this
    var end = n
    if (end > 0 && this[end - 1].isHighSurrogate()) end--
    return substring(0, end)
}
