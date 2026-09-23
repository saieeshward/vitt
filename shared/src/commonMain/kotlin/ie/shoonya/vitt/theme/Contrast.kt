package ie.shoonya.vitt.theme

import kotlin.math.pow

/**
 * WCAG 2.x contrast, so the theme test can say "this fails" in the same terms
 * an accessibility audit would.
 *
 * Doubles are fine here: this is colour science, not money, and nothing that
 * comes out of it is stored.
 */
object Contrast {

    /** Relative luminance of an opaque ARGB value, per WCAG 2.x. */
    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Contrast ratio between two opaque colours, from 1.0 up to 21.0. */
    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }
}
