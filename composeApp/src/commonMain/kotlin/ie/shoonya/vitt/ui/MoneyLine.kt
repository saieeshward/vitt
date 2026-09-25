package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * One amount, rendered by the money rules.
 *
 * Three constraints, all from the identity and all easy to violate accidentally:
 *
 * - **A figure never appears without its own currency symbol.** There is no
 *   default currency in this app, so a bare number is always ambiguous.
 * - **Spending is plain text.** Only income and settled debts take colour;
 *   colouring an expense red would make the app deliver a verdict on it.
 * - **No converted equivalent, ever.** There is no parameter here to pass one,
 *   which is the point.
 */
@Composable
fun MoneyLine(
    money: Money,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    currencyIndex: Int? = null,
) {
    val colors = Vitt.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // The currency's square: chroma lives in marks and lines, never in a
        // filled card or a coloured amount.
        currencyIndex?.let { index ->
            CurrencyMark(index, size = 6.dp())
            Spacer(Modifier.width(Vitt.space.tight))
        }
        Text(
            text = money.display(),
            style = if (hero) Vitt.type.moneyHero else Vitt.type.money,
            color = if (money.isInflow) colors.accentSoft else colors.ink,
        )
    }
}

/**
 * A split expense: your share is the large figure, because your share is what
 * the budget and the categories actually see. What you paid is demoted beneath.
 */
@Composable
fun SplitMoneyLine(
    yourShare: Money,
    totalPaid: Money,
    owedBy: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Vitt.space.hair)) {
        MoneyLine(yourShare, hero = true)
        Text(
            text = buildString {
                append("your share · paid ${totalPaid.display()}")
                owedBy?.let { append(" · $it owes ${(totalPaid - yourShare).display()}") }
            },
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
