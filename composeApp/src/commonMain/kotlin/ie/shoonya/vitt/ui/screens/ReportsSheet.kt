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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.stateDescription
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.model.CategoryMove
import ie.shoonya.vitt.model.Comparison
import ie.shoonya.vitt.model.Projection
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
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.Period
import ie.shoonya.vitt.time.periodLabel
import ie.shoonya.vitt.time.periodPhrase
import ie.shoonya.vitt.ui.VittChip
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
    received: Money,
    budget: Money?,
    trend: List<PeriodSlice>,
    categories: List<CategorySlice>,
    merchants: List<MerchantSlice>,
    /** This period beside the last. Null for all-time, which has no "last". */
    comparison: Comparison?,
    /** Where the current month is heading. Null outside it, or before the 7th. */
    projection: Projection?,
    /** The merchants inside one category, for the drilldown. */
    merchantsIn: (Category?) -> List<MerchantSlice>,
    /** Running total per day of the current month, empty outside a month. */
    cumulative: List<Long>,
    daysInMonth: Int,
    /** Spend by weekday, Monday first. */
    weekday: List<Money>,
    /** The month being shown, when the period is one; the calendar needs its weekdays. */
    month: ie.shoonya.vitt.time.YearMonth? = null,
    /** What went out each day of [month] so far. */
    daily: List<Money> = emptyList(),
    /** How big purchases were, between round amounts. */
    sizes: List<ie.shoonya.vitt.model.SizeBin> = emptyList(),
    /** The first day anything was recorded; the calendar counts from there. */
    trackedFrom: Int? = null,
    /** The month told back as a story, when there is one to tell. */
    review: ie.shoonya.vitt.model.MonthReview? = null,
    onOpenReview: () -> Unit = {},
    /** Opens the one-tap-each pass over entries with no category. */
    onSort: (() -> Unit)? = null,
    onExport: () -> Unit,
    onImport: () -> Unit,
    /** Null when this is a tab rather than a sheet: there is nothing to be done with. */
    onDone: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose)
            // Clear of the home indicator, so the last control is not the one
            // the thumb is resting over.
            .padding(bottom = Vitt.space.section * 2),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Reports",
                style = if (onDone == null) Vitt.type.display else Vitt.type.title,
                color = Vitt.colors.ink,
            )
            onDone?.let { TextButton(onClick = it) { Text("Done") } }
        }

        // Only worth the space once there is more than one currency — and unlike
        // the Activity chips there is deliberately no "All".
        if (currencies.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
                currencies.forEach { c ->
                    VittChip(
                        selected = c == currency,
                        onClick = { onCurrencyChange(c) },
                        label = { Text(c.code, style = Vitt.type.label) },
                    )
                }
            }
        }

        // The one loud figure on this screen answers the question people
        // actually open Reports with: am I up or down. In against out, with
        // the direction in a word rather than a sign. Only once there is an
        // in: with no income recorded the net is just the spend with a minus
        // on it, and reading "Down €1,200" to someone who never logs a salary
        // would be the app inventing a loss. Those people see spend, as before.
        val net = received - spent
        if (received.minor > 0) {
            Text(
                when {
                    net.minor > 0 -> "Up ${net.displayUnsigned()}"
                    net.minor < 0 -> "Down ${net.displayUnsigned()}"
                    else -> "Level"
                },
                style = Vitt.type.moneyHero,
                color = Vitt.colors.ink,
            )
            // The budget is not mentioned here: it bounds spend, not net, and
            // "Down €500 · budget €2,000" read as over budget when the spend
            // was well under it. The pace line and the chart carry the budget.
            Text(
                "${received.displayUnsigned()} in, ${spent.displayUnsigned()} out ${periodPhrase(period, today)}",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        } else {
            Text(spent.displayUnsigned(), style = Vitt.type.moneyHero, color = Vitt.colors.ink)
            Text(
                "out ${periodPhrase(period, today)}" +
                    (budget?.let { " · of ${it.displayUnsigned()}" } ?: "") +
                    " · no income recorded",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        }

        // A reading, not a forecast figure. The user asked for "am I burning
        // through it, fine, or well under", and that is the honest use of a
        // projection anyway: §5.3's range is what the reading rests on, not a
        // number to be quoted back. Against the budget where there is one,
        // otherwise against the user's own usual month. Behaviour is
        // described, never the person (§5.6).
        projection?.pace(budget)?.let { pace ->
            val against = if (budget != null) "the budget" else "your usual month"
            Text(
                when (pace) {
                    Projection.Pace.AHEAD -> "Running ahead of $against. This pace spends it before the month ends."
                    Projection.Pace.ON -> "On pace with $against."
                    Projection.Pace.UNDER -> "Well under $against. More room than usual."
                },
                style = Vitt.type.body,
                color = Vitt.colors.ink,
            )
            Text(
                if (projection.rough) "A rough read until there is more history to go on."
                else "Read against your own past months, from day ${Civil.fromDays(today).third} of the month.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        }

        review?.let {
            ie.shoonya.vitt.ui.screens.ReviewCard(review = it, hue = currencyIndex(currency), onOpen = onOpenReview)
        }

        // Wide windows read in two columns: how the money moved on the left,
        // where it went on the right. A phone reads them one after the other.
        TwoColumns(
            wide = ie.shoonya.vitt.ui.LocalWindowLayout.current.width == ie.shoonya.vitt.layout.WindowLayout.Width.EXPANDED,
            first = {
            // The month so far as a line, against the even pace a budget implies.
            // A line rather than a figure because the *shape* is the information:
            // rent on the 1st is a step, a trip is a jump, groceries are a slope.
            if (cumulative.isNotEmpty() && daysInMonth > 0) {
                SectionHeader("This month, day by day")
                CumulativeChart(
                    cumulative = cumulative,
                    daysInMonth = daysInMonth,
                    budget = budget,
                    hue = currencyIndex(currency),
                    currency = currency,
                )
                if (month != null && daily.isNotEmpty()) {
                    ie.shoonya.vitt.ui.DayDots(month = month, daily = daily, today = today, hue = currencyIndex(currency), since = trackedFrom)
                }
            }

            // In and out, only once there is an in. A section reading "In €0.00"
            // on every screen would be a reminder of what is not being recorded.
            // The headline above already says the net; this is the working.
            if (received.minor > 0) {
                SectionHeader("In and out")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Figure("In", received.displayUnsigned())
                    Figure("Out", spent.displayUnsigned())
                    // The headline's own word, so the two figures never disagree
                    // and nothing here reads as a verdict on the person (§5.6).
                    Figure(
                        if (net.minor >= 0) "Kept" else "Down",
                        net.displayUnsigned(),
                    )
                }
                Text(
                    if (net.minor >= 0) {
                        "What came in covered what went out."
                    } else {
                        "More went out than came in ${periodPhrase(period, today)}."
                    },
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
            }

            // Only with something to compare against. A heading over "nothing
            // recorded last month" was a row of screen spent saying nothing,
            // on every first month anybody has.
            comparison?.takeIf { it.spentBefore.minor > 0 }?.let { c ->
                SectionHeader("Against ${periodLabel(c.before, today)}")
                ComparisonSection(c, today)
            }

            // A trend needs two points. One month of history drew half a
            // screen of empty axis with a single line at its end, so the
            // section waits until there is a second period to draw.
            if (trend.count { it.spent.minor > 0 || it.received.minor > 0 } >= 2) {
                SectionHeader("Trend")
                TrendChart(trend = trend, hue = currencyIndex(currency), today = today)
            } else if (trend.isEmpty()) {
                SectionHeader("Trend")
                Text(
                    // All-time is the absence of a period, and a trend needs a grain
                    // to step along. Saying so is better than drawing one bar.
                    "All time has no grain to plot. Pick a period on Ledgers.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
            }

            },
            second = {
            SectionHeader("Categories")
            if (categories.isEmpty()) {
                Text(
                    "Nothing recorded in ${currency.code} ${periodPhrase(period, today)}.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
            } else {
                // One open at a time: the drilldown is a glance at what is behind
                // a row, not a second list to scroll.
                var open by remember(currency, period) { mutableStateOf<Category?>(null) }
                var openNone by remember(currency, period) { mutableStateOf(false) }
                // The ring leads, with the rows below as its legend: a glance at
                // the shape first, then the names and figures. At most six arcs,
                // the tail folded, and one in the hue at a time.
                val ring = remember(categories) { ie.shoonya.vitt.model.Insights.foldTail(categories) }
                var ringFocus by remember(currency, period) { mutableStateOf(0) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ie.shoonya.vitt.ui.CategoryRing(
                        slices = ring,
                        total = spent,
                        hue = currencyIndex(currency),
                        focused = ringFocus,
                        onFocus = { ringFocus = it },
                    )
                }
                categories.forEach { slice ->
                    val isOpen = if (slice.category == null) openNone else open == slice.category
                    CategoryRow(
                        slice = slice,
                        total = spent,
                        hue = currencyIndex(currency),
                        open = isOpen,
                        onToggle = {
                            if (slice.category == null) { openNone = !openNone; open = null }
                            else { open = if (isOpen) null else slice.category; openNone = false }
                            ring.indexOfFirst { it.category == slice.category }.takeIf { it >= 0 }?.let { ringFocus = it }
                        },
                    )
                    if (isOpen) {
                        val inside = merchantsIn(slice.category)
                        if (inside.isEmpty()) {
                            Text(
                                "No merchant named on these.",
                                style = Vitt.type.label,
                                color = Vitt.colors.inkMuted,
                                modifier = Modifier.padding(start = Vitt.space.loose),
                            )
                        } else {
                            Column(modifier = Modifier.padding(start = Vitt.space.loose)) {
                                inside.forEach { MerchantRow(it) }
                            }
                        }
                    }
                }
                // The way to shrink the "No category" row, beside it, rather than
                // a trip through Activity opening entries one sheet at a time.
                if (onSort != null && categories.any { it.category == null }) {
                    ie.shoonya.vitt.ui.VittChip(
                        selected = false,
                        onClick = onSort,
                        label = { Text("Sort the ones with no category", style = Vitt.type.label) },
                    )
                }
                Text(
                    // Worth stating, because it is the reason the "No category" row
                    // is on the chart at all rather than quietly dropped.
                    "These add up to the figure above.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkFaint,
                )
            }

            if (weekday.any { it.minor > 0 }) {
                SectionHeader("By weekday")
                WeekdayDots(weekday = weekday, hue = currencyIndex(currency))
            }

            if (sizes.sumOf { it.count } >= MIN_FOR_SIZES) {
                SectionHeader("Purchase sizes")
                ie.shoonya.vitt.ui.SizeDots(bins = sizes, hue = currencyIndex(currency))
            }

            if (merchants.isNotEmpty()) {
                SectionHeader("Where it went")
                merchants.forEach { MerchantRow(it) }
            }

            },
        )

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
            // test is marketing. This is where they test it. Directly under the
            // row it describes: stacked at the end of the section, both captions
            // read as belonging to whichever row happened to be last.
            "Every transaction and transfer, in every currency. The data, not a summary.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Import a bank export",
                style = Vitt.type.body,
                color = Vitt.colors.ink,
            )
            TextButton(onClick = onImport) { Text("Import") }
        }
        Text(
            "A CSV from your bank. Everything is shown before anything is saved.",
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
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        Text(value, style = Vitt.type.money, color = Vitt.colors.ink)
    }
}

/**
 * What changed since last period, in the words §5.6 allows.
 *
 * "More" and "less" are directions, not grades. The figures are drawn in ink,
 * never in a status colour: a month that ran higher is described, not marked.
 * Three movers is the ceiling, because the point is *the* category that moved.
 */
@Composable
private fun ComparisonSection(c: Comparison, today: Int) {
    // Never called with an empty last period: that is missing data, not a
    // baseline of zero (§5.6), and comparing against it would call every
    // first month a rise. The caller leaves the section out instead.
    val before = periodLabel(c.before, today)
    val headline = when {
        c.delta.minor == 0L -> "The same as $before."
        c.delta.minor > 0 -> "${c.delta.displayUnsigned()} more than $before."
        else -> "${c.delta.displayUnsigned()} less than $before."
    }
    Text(headline, style = Vitt.type.body, color = Vitt.colors.ink)
    c.movers.take(3).forEach { MoverRow(it) }
    if (c.movers.size == 1) {
        Text(
            "The one category that moved.",
            style = Vitt.type.label,
            color = Vitt.colors.inkFaint,
        )
    }
}

@Composable
private fun MoverRow(move: CategoryMove) {
    val colors = Vitt.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Vitt.space.hair)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.6f)) {
            Text(move.label, style = Vitt.type.body, color = colors.ink, maxLines = 1)
            Text(
                "${move.before.displayUnsigned()} to ${move.now.displayUnsigned()}",
                style = Vitt.type.caption,
                color = colors.inkMuted,
            )
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                // A real minus sign and a plus, in the same ink. The sign is the
                // information; a colour would be a verdict.
                (if (move.delta.minor > 0) "+" else "\u2212") + move.delta.displayUnsigned(),
                style = Vitt.type.money,
                color = colors.ink,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    slice: CategorySlice,
    total: Money,
    hue: Int,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val colors = Vitt.colors
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (open) "Expanded" else "Collapsed"
            }
            .padding(vertical = Vitt.space.hair),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // How many times, as marks beside how much: a category that
                // is forty small things reads differently from one big one.
                CountMarks(slice.count, if (slice.category == null) null else hue)
                Text(
                    slice.spent.displayUnsigned(),
                    style = Vitt.type.money,
                    color = colors.ink,
                    modifier = Modifier.padding(start = Vitt.space.snug),
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

/** One small square per record, up to a handful; past that the number says it. */
@Composable
private fun CountMarks(count: Int, hue: Int?) {
    val colors = Vitt.colors
    val color = hue?.let { colors.currency(it) } ?: colors.inkFaint
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (count > MARKS_SHOWN) {
            Text("×$count", style = Vitt.type.mono, color = colors.inkMuted, modifier = Modifier.padding(end = 2.dp))
        } else {
            repeat(count) { Box(Modifier.size(4.dp).background(color.copy(alpha = 0.7f))) }
        }
    }
}

private const val MARKS_SHOWN = 12

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

/**
 * The running total, with a dotted guide from zero to the budget across the
 * month, so where the line sits against the guide is readable without a
 * number: above it is faster than even pace, below it slower. One hue, the
 * currency's, and the guide in neutral ink. Not a verdict either way — the
 * pace line is what the budget *implies*, and many sane months front-load.
 */
@Composable
private fun CumulativeChart(
    cumulative: List<Long>,
    daysInMonth: Int,
    budget: Money?,
    hue: Int,
    currency: Currency,
) {
    val colors = Vitt.colors
    val tint = colors.currency(hue)
    val soFar = cumulative.last()
    val top = maxOf(soFar, budget?.minor ?: 0L, 1L)
    val spoken = "Spent so far ${Money(soFar, currency).displayUnsigned()} by day ${cumulative.size} of $daysInMonth" +
        (budget?.let { ", budget ${it.displayUnsigned()}" } ?: "")
    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
        Canvas(
            modifier = Modifier.fillMaxWidth().height(96.dp)
                .semantics { contentDescription = spoken },
        ) {
            val w = size.width
            val h = size.height
            val stroke = 2.dp.toPx()
            fun x(day: Int) = w * (day.toFloat() / daysInMonth.toFloat())
            fun y(minor: Long) = h - stroke - (h - 2 * stroke) * (minor.toFloat() / top.toFloat())

            // Baseline.
            drawLine(colors.inkFaint.copy(alpha = 0.35f), Offset(0f, h - stroke / 2), Offset(w, h - stroke / 2), strokeWidth = 1.dp.toPx())

            // The even-pace guide, from zero on day 0 to the budget on the last day.
            budget?.let { b ->
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                drawLine(
                    colors.inkFaint,
                    Offset(0f, y(0)),
                    Offset(w, y(b.minor)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
            }

            // The month so far.
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, y(0))
            cumulative.forEachIndexed { i, v -> path.lineTo(x(i + 1), y(v)) }
            drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            // Today, marked, with a surface ring so it sits on the line rather than in it.
            val end = Offset(x(cumulative.size), y(soFar))
            drawCircle(colors.ground, radius = 5.dp.toPx(), center = end)
            drawCircle(tint, radius = 3.5f.dp.toPx(), center = end)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", style = Vitt.type.caption, color = colors.inkFaint)
            Text(
                "day ${cumulative.size}" + (budget?.let { " · dotted line is even pace to ${it.displayUnsigned()}" } ?: ""),
                style = Vitt.type.caption,
                color = colors.inkMuted,
            )
            Text("$daysInMonth", style = Vitt.type.caption, color = colors.inkFaint)
        }
    }
}

/**
 * Seven dots, sized by how much went out on that weekday. Magnitude as area,
 * one hue; the heaviest day carries its figure and the rest do not, so the
 * row says one thing rather than seven.
 */
@Composable
private fun WeekdayDots(weekday: List<Money>, hue: Int) {
    val colors = Vitt.colors
    val tint = colors.currency(hue)
    val peak = weekday.maxOf { it.minor }.coerceAtLeast(1L)
    val heaviest = weekday.indices.maxByOrNull { weekday[it].minor } ?: 0
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val spoken = weekday.mapIndexed { i, m -> "${names[i]} ${m.displayUnsigned()}" }.joinToString(", ")
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Spending by weekday: $spoken" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        weekday.forEachIndexed { i, m ->
            val share = kotlin.math.sqrt(m.minor.toFloat() / peak.toFloat())
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
                Text(
                    if (i == heaviest && m.minor > 0) m.displayUnsigned() else "",
                    style = Vitt.type.caption,
                    color = colors.inkMuted,
                    maxLines = 1,
                )
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size((6f + 22f * share).dp).clip(CircleShape)
                            .background(if (m.minor > 0) tint.copy(alpha = 0.35f + 0.65f * share) else colors.inkFaint.copy(alpha = 0.25f)),
                    )
                }
                Text(names[i], style = Vitt.type.caption, color = if (i == heaviest) colors.ink else colors.inkFaint)
            }
        }
    }
}

/**
 * Below this many purchases a size chart is a handful of dots that says
 * nothing a list would not.
 */
private const val MIN_FOR_SIZES = 8

/** Two columns side by side when [wide], otherwise one after the other. */
@Composable
private fun TwoColumns(wide: Boolean, first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val gap = Vitt.space.base
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Vitt.space.section)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) { first() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) { second() }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            first()
            second()
        }
    }
}
