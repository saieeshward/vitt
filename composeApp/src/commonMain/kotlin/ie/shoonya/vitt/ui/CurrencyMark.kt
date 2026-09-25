package ie.shoonya.vitt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * The currency's mark: a square, not a dot.
 *
 * Every companion carries its coins in a 2 by 2 pixel slot on its back, one
 * slot per currency, and this is that slot at interface size. The same square
 * sits beside every figure, on every card, row and chart, so "money stays in
 * its own pot" is felt on each screen without ever being read. Chroma lives
 * here and nowhere else: never in a filled card, never in an amount.
 */
@Composable
fun CurrencyMark(hue: Int, modifier: Modifier = Modifier, size: Dp = 8.dp, alpha: Float = 1f) {
    Box(modifier.size(size).background(Vitt.colors.currency(hue).copy(alpha = alpha)))
}
