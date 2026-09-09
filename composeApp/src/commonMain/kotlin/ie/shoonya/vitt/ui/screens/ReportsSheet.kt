package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.model.CategorySlice
import ie.shoonya.vitt.model.MerchantSlice
import ie.shoonya.vitt.model.PeriodSlice
import ie.shoonya.vitt.model.peakSpent
import ie.shoonya.vitt.model.shareOf
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Period
import ie.shoonya.vitt.time.periodLabel
import ie.shoonya.vitt.time.periodPhrase
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Reports, reached from the Ledgers header rather than a tab.
 *
 * `design-identity.md` puts reports and settings under Ledgers' header icons for
 * a stated reason: a monthly visit must not slow the daily path. So this is an
 * icon, not a fifth tab, and the tab bar keeps its four.
 *
 * **One currency at a time, always.** The currency is a required argument rather
 * than a filter with an "All" option, because there is no chart that can be
 * drawn across currencies — §0.6 forbids the figure, and [ie.shoonya.vitt.model.Insights]
 * refuses to compute it. The chips here therefore switch between currencies and
 * never offer a combined view, which is the one way this screen differs from the
 * Activity filters it otherwise resembles.
 *
 * The period comes from the app-wide control, so pulling up August on Ledgers
 * and opening Reports shows August. It is not restated here as a second control
 * — two period pickers disagreeing would be worse than one being a screen away.
 */
@Composable
fun ReportsSheet(
    currency: Currency,
    currencies: List<Currency>,
    currencyIndex: (Currency) -> Int,
    onCurrencyChange: (Currency) -> Unit,
    period: Period?,
    today: Int,
    spent: Money,
    budget: Money?,
    trend: List<PeriodSlice>,
    categories: List<CategorySlice>,
    merchants: List<MerchantSlice>,
    onExport: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reports", style = Vitt.type.title, color = Vitt.colors.ink)
            TextButton(onClick = onDone) { Text("Done") }
        }

        // Only worth the space once there is more than one currency — and unlike
        // the Activity chips there is deliberately no "All".
        if (currencies.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
                currencies.forEach { c ->
                    FilterChip(
                        selected = c == currency,
                        onClick = { onCurrencyChange(c) },
                        label = { Text(c.code, style = Vitt.type.label) },
                    )
                }
            }
        }

        // The one loud number on this screen, and it states its own direction
        // rather than carrying a minus sign.
        Text(spent.displayUnsigned(), style = Vitt.type.moneyHero, color = Vitt.colors.ink)
        Text(
            "out ${periodPhrase(period, today)}" +
                (budget?.let { " · of ${it.displayUnsigned()}" } ?: ""),
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        if (trend.isNotEmpty()) {
            SectionHeader("Trend")
            TrendChart(trend = trend, hue = currencyIndex(currency), today = today)
        } else {
            SectionHeader("Trend")
            Text(
                // All-time is the absence of a period, and a trend needs a grain
                // to step along. Saying so is better than drawing one bar.
                "All time has no grain to plot. Pick a period on Ledgers.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        }

        SectionHeader("Categories")
        if (categories.isEmpty()) {
            Text(
                "Nothing recorded in ${currency.code} ${periodPhrase(period, today)}.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        } else {
            categories.forEach { CategoryRow(it, spent, currencyIndex(currency)) }
            Text(
                // Worth stating, because it is the reason the "No category" row
                // is on the chart at all rather than quietly dropped.
                "These add up to the figure above.",
                style = Vitt.type.label,
                color = Vitt.colors.inkFaint,
            )
        }

        if (merchants.isNotEmpty()) {
            SectionHeader("Where it went")
            merchants.forEach { MerchantRow(it) }
        }

        SectionHeader("Your data")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Export everything as CSV",
                style = Vitt.type.body,
                color = Vitt.colors.ink,
            )
            TextButton(onClick = onExport) { Text("Export") }
        }
        Text(
            // The no-lock-in premise is a claim, and a claim the user cannot
            // test is marketing. This is where they test it.
            "Every transaction and transfer, in every currency. The data, not a summary.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = Vitt.type.caption,
        color = Vitt.colors.inkMuted,
        modifier = Modifier.padding(top = Vitt.space.snug),
    )
}

/**
 * Spending across consecutive periods, drawn as strokes on a baseline.
 *
 * Strokes rather than filled columns, because `design-identity.md` says chroma
 * lives in lines, dots and glows and **never in a filled bar** — that rule is
 * what keeps the palette working at four or five currencies, and a chart is
 * exactly where it is most tempting to break it.
 *
 * One hue for the whole chart: the currency's own. Colour in this app carries
 * *which currency*, never *how you are doing*, so colouring the tallest bar
 * differently would be the chart passing a verdict.
 *
 * The period the user is standing in is drawn at full strength and the earlier
 * ones are dimmed, so "now" is findable without a label on every bar.
 */
@Composable
private fun TrendChart(trend: List<PeriodSlice>, hue: Int, today: Int) {
    val colors = Vitt.colors
    val peak = trend.peakSpent()
    val tint = colors.currency(hue)

    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
        // Every other number in this app is text somewhere. These bars are not,
        // so the chart states itself: without this the whole trend is silence.
        val spoken = trend.joinToString(", ") { slice ->
            "${periodLabel(slice.period, today)} ${slice.spent.displayUnsigned()}"
        }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(72.dp)
                .semantics { contentDescription = "Spending by period: $spoken" },
        ) {
            val slotWidth = size.width / trend.size
            // A fixed fraction of the slot, so the bars do not fatten into
            // blocks when the trend is short.
            val strokeWidth = (slotWidth * 0.18f).coerceAtMost(10f)
            val baseline = size.height

            trend.forEachIndexed { i, slice ->
                val x = slotWidth * (i + 0.5f)
                val fraction =
                    if (peak == 0L) 0f else slice.spent.minor.toFloat() / peak.toFloat()
                val isNow = today in slice.period
                // A period with nothing in it still gets a mark — a dot on the
                // baseline. Drawing nothing would make an empty month look like
                // a month that is not on the axis.
                if (fraction <= 0f) {
                    drawCircle(
                        color = colors.inkFaint,
                        radius = strokeWidth * 0.42f,
                        center = Offset(x, baseline - strokeWidth * 0.42f),
                    )
                } else {
                    val height = (size.height - strokeWidth) * fraction + strokeWidth
                    drawLine(
                        color = if (isNow) tint else tint.copy(alpha = 0.45f),
                        start = Offset(x, baseline),
                        end = Offset(x, baseline - height),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        // Only the ends are labelled. A label under every bar at this width
        // overlaps, and the middle periods are readable by position.
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                periodLabel(trend.first().period, today),
                style = Vitt.type.caption,
                color = colors.inkFaint,
                modifier = Modifier.weight(1f),
            )
            Text(
                periodLabel(trend.last().period, today),
                style = Vitt.type.caption,
                color = colors.inkMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryRow(slice: CategorySlice, total: Money, hue: Int) {
    val colors = Vitt.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Vitt.space.hair),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                slice.label,
                style = Vitt.type.body,
                color = if (slice.category == null) colors.inkMuted else colors.ink,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    slice.spent.displayUnsigned(),
                    style = Vitt.type.money,
                    color = colors.ink,
                )
            }
        }
        // The same 3dp hairline the budget health bar uses, so the two read as
        // one system — and thin enough to stay a line rather than become the
        // filled bar the identity rules out.
        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                .background(colors.inkFaint.copy(alpha = 0.18f)),
        ) {
            Box(
                Modifier
                    // coerceAtLeast so a rounding-dust category is still visibly
                    // present rather than an invisible row with an amount.
                    .fillMaxWidth(slice.shareOf(total).coerceIn(0.015f, 1f))
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        // Uncategorised is drawn in neutral, not the currency
                        // hue: it is a prompt to act, not one of the categories
                        // competing on the chart.
                        if (slice.category == null) {
                            colors.inkFaint
                        } else {
                            colors.currency(hue)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun MerchantRow(slice: MerchantSlice) {
    val colors = Vitt.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Vitt.space.hair),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.6f)) {
            Text(slice.label, style = Vitt.type.body, color = colors.ink, maxLines = 1)
            Text(
                if (slice.count == 1) "once" else "${slice.count} times",
                style = Vitt.type.caption,
                color = colors.inkMuted,
            )
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(slice.spent.displayUnsigned(), style = Vitt.type.money, color = colors.ink)
        }
    }
}
