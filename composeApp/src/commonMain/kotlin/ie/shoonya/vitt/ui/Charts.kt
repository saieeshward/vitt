package ie.shoonya.vitt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.model.CategorySlice
import ie.shoonya.vitt.model.SizeBin
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import ie.shoonya.vitt.ui.theme.Vitt
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The charts, drawn the way the design identity allows: chroma in lines, dots
 * and glows, never a filled bar or a coloured card. One currency is one hue,
 * and a chart never borrows another currency's hue to tell things apart, so
 * where several things share a chart the form is emphasis: the one being looked
 * at in the hue, the rest in neutral. That is also what survives colour
 * blindness, which a ring of five tints of one green did not (checked with the
 * dataviz palette validator: adjacent tints failed even for normal vision).
 *
 * No depth or perspective on anything that encodes money. 3D pies are read less
 * accurately than flat ones (Siegrist 1996), because the tilt changes how big a
 * slice looks and hides the ones at the back.
 */

/** A money amount as an axis label: `€5`, `€12.50`, never a bare number. */
internal fun Money.compact(): String {
    val full = AmountEntry.formatMinor(minor, currency)
    return if (currency.exponent > 0 && minor % pow10(currency.exponent) == 0L) {
        full.replace(Regex("""[.,]0+(?=\D*$)"""), "")
    } else {
        full
    }
}

private fun pow10(n: Int): Long = (1..n).fold(1L) { a, _ -> a * 10 }

/**
 * Where the money went, as a ring: each category an arc, one of them in the
 * currency's hue with its figure in the middle, the rest neutral.
 *
 * [focused] is the index into [slices] shown in the hue; tapping an arc
 * reports its index. The list of categories beneath the ring is its legend and
 * carries the names, so nothing here depends on telling colours apart.
 */
@Composable
fun CategoryRing(
    slices: List<CategorySlice>,
    total: Money,
    hue: Int,
    focused: Int,
    onFocus: (Int) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 188.dp,
    /** False where a tap means something else, as on a story page that turns. */
    interactive: Boolean = true,
) {
    val colors = Vitt.colors
    val focus = slices.getOrNull(focused)
    val shares = slices.map { if (total.minor == 0L) 0f else it.spent.minor.toFloat() / total.minor }
    Box(
        modifier = modifier
            .size(diameter)
            .semantics {
                contentDescription = slices.take(6).joinToString(", ", prefix = "Where it went: ") {
                    "${it.label} ${it.spent.displayUnsigned()}"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(diameter)
                .pointerInput(slices, interactive) {
                    if (!interactive) return@pointerInput
                    detectTapGestures { at ->
                        // The angle from twelve o'clock, clockwise, picks the arc.
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val deg = ((atan2(at.y - c.y, at.x - c.x) * 180 / PI + 90 + 360) % 360).toFloat()
                        var start = 0f
                        shares.forEachIndexed { i, share ->
                            val sweep = share * 360f
                            if (deg >= start && deg < start + sweep) {
                                onFocus(i); return@detectTapGestures
                            }
                            start += sweep
                        }
                    }
                },
        ) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            // A 2dp gap of ground between arcs, as an angle at this radius.
            val gap = if (slices.size > 1) (2.dp.toPx() / (arcSize.width / 2) * 180 / PI).toFloat() else 0f
            var start = -90f
            shares.forEachIndexed { i, share ->
                val sweep = share * 360f
                if (sweep > gap) {
                    drawArc(
                        color = if (i == focused) colors.currency(hue) else colors.inkFaint.copy(alpha = 0.32f),
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = if (i == focused) stroke else stroke * 0.7f),
                    )
                }
                start += sweep
            }
        }
        focus?.let { slice ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(slice.label, style = Vitt.type.label, color = colors.inkMuted, maxLines = 1)
                Text(slice.spent.displayUnsigned(), style = Vitt.type.money, color = colors.ink)
                val pct = (shares[focused] * 100).toInt()
                Text("$pct% of ${total.compact()}", style = Vitt.type.label, color = colors.inkFaint)
            }
        }
    }
}

/**
 * The month as a calendar of dots: the bigger the dot, the more went out that
 * day. A day nothing went out is a hollow ring, drawn and not left blank,
 * because a clear day is something that happened rather than something missing,
 * and the widget already counts it in the person's favour.
 *
 * Dot *area* follows the amount, so a day twice as dear looks twice as big,
 * not four times.
 */
@Composable
fun DayDots(
    month: YearMonth,
    daily: List<Money>,
    today: Int,
    hue: Int,
    modifier: Modifier = Modifier,
    /** False where a tap means something else, as on a story page that turns. */
    interactive: Boolean = true,
) {
    val colors = Vitt.colors
    var picked by remember(month, daily) { mutableStateOf<Int?>(null) }
    val max = daily.maxOfOrNull { it.minor }?.coerceAtLeast(1L) ?: 1L
    val lead = Civil.dayOfWeek(month.firstDay)
    val days = Civil.daysInMonth(month.year, month.month)
    val clear = daily.count { it.minor == 0L }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vitt.space.hair)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = Vitt.type.label, color = colors.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val cells = lead + days
        (0 until ceil(cells / 7.0).toInt()).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { col ->
                    val index = week * 7 + col - lead
                    val amount = daily.getOrNull(index)
                    val isFuture = index >= 0 && index < days && month.firstDay + index > today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .let { m ->
                                if (amount == null || !interactive) m else m.clickable { picked = if (picked == index) null else index }
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "${index + 1}: " +
                                            if (amount.minor == 0L) "nothing out" else amount.displayUnsigned()
                                    }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index in 0 until days) {
                            Canvas(Modifier.size(30.dp)) {
                                val r = size.minDimension / 2
                                when {
                                    isFuture -> drawCircle(colors.inkFaint.copy(alpha = 0.18f), radius = 2.dp.toPx())
                                    amount == null -> Unit
                                    amount.minor == 0L -> drawCircle(
                                        colors.currency(hue).copy(alpha = 0.55f),
                                        radius = r * 0.42f,
                                        style = Stroke(width = 1.5.dp.toPx()),
                                    )
                                    else -> drawCircle(
                                        colors.currency(hue).copy(alpha = if (picked == index) 1f else 0.85f),
                                        radius = (r * sqrt(amount.minor.toFloat() / max)).coerceAtLeast(3.dp.toPx()),
                                    )
                                }
                                if (picked == index) drawCircle(colors.ink, radius = r, style = Stroke(1.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
        val caption = picked?.let { i ->
            val d = daily[i]
            "${i + 1} ${monthName(month.month)}: " + if (d.minor == 0L) "nothing out" else d.displayUnsigned()
        } ?: run {
            // "So far" only while the month is still going; a finished month
            // is described as it was.
            val span = if (today < month.lastDay) " so far" else " in ${monthName(month.month)}"
            when (clear) {
                0 -> "Tap a day to see it."
                1 -> "1 day with nothing out$span."
                else -> "$clear days with nothing out$span."
            }
        }
        Text(caption, style = Vitt.type.label, color = colors.inkMuted)
    }
}

/**
 * How big purchases are, as columns of dots: one dot a purchase, stacked from
 * the bottom, between round amounts. A histogram without a filled bar, and one
 * a person can count. Past [perColumn] dots in the busiest column each dot
 * stands for several purchases, and the caption says how many.
 */
@Composable
fun SizeDots(
    bins: List<SizeBin>,
    hue: Int,
    modifier: Modifier = Modifier,
    perColumn: Int = 12,
) {
    val colors = Vitt.colors
    var picked by remember(bins) { mutableStateOf<Int?>(null) }
    val busiest = bins.maxOfOrNull { it.count } ?: 0
    val each = if (busiest <= perColumn) 1 else ceil(busiest / perColumn.toDouble()).toInt()
    val modal = bins.withIndex().maxByOrNull { it.value.count }?.index
    // Two dots a row, so the busiest column sets the height and nothing floats
    // in an empty band above the dots.
    val rows = ceil(ceil(busiest / each.toDouble()) / 2).toInt().coerceAtLeast(1)
    val chartHeight = DOT * rows + DOT_GAP * (rows - 1)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vitt.space.hair)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            verticalAlignment = Alignment.Bottom,
        ) {
            bins.forEachIndexed { i, bin ->
                val dots = ceil(bin.count / each.toDouble()).toInt()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(chartHeight)
                        .clickable { picked = if (picked == i) null else i }
                        .semantics {
                            role = Role.Button
                            contentDescription = "${label(bin)}: ${bin.count} purchases"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                        val r = min(size.width / 5, DOT.toPx() / 2)
                        val step = r * 2 + DOT_GAP.toPx()
                        // Two dots a row, so a column of twelve is six rows high.
                        (0 until dots).forEach { d ->
                            val row = d / 2
                            val x = size.width / 2 + (if (d % 2 == 0) -1 else 1) * (r + 1.dp.toPx())
                            val y = size.height - r - row * step
                            drawCircle(
                                colors.currency(hue).copy(alpha = if (picked == null || picked == i) 0.9f else 0.35f),
                                radius = r,
                                center = Offset(x, y),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            bins.forEach { bin ->
                Text(
                    label(bin),
                    style = Vitt.type.label,
                    color = colors.inkFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val caption = picked?.let { i ->
            val bin = bins[i]
            "${label(bin)}: ${bin.count} ${if (bin.count == 1) "purchase" else "purchases"}, ${bin.spent.displayUnsigned()} in all"
        } ?: modal?.let { "Most purchases are ${phrase(bins[it])}." }
        caption?.let { Text(it, style = Vitt.type.label, color = colors.inkMuted) }
        if (each > 1) Text("Each dot is $each purchases.", style = Vitt.type.label, color = colors.inkFaint)
    }
}

/**
 * A split as a small ring: the user's part in the accent, everybody else's in
 * neutral. Emphasis rather than a colour per person, because the one figure
 * that matters on a split is what the user is out of pocket, and naming five
 * friends with five hues would be a legend to decode.
 */
@Composable
fun SplitRing(yours: Money, others: List<Money>, modifier: Modifier = Modifier, diameter: Dp = 56.dp) {
    val colors = Vitt.colors
    val total = yours.minor + others.sumOf { it.minor }
    Canvas(modifier.size(diameter)) {
        if (total <= 0L) return@Canvas
        val stroke = 7.dp.toPx()
        val arc = Size(size.width - stroke, size.height - stroke)
        val parts = listOf(yours.minor) + others.map { it.minor }
        val gap = if (parts.count { it > 0 } > 1) (2.dp.toPx() / (arc.width / 2) * 180 / PI).toFloat() else 0f
        var start = -90f
        parts.forEachIndexed { i, part ->
            val sweep = part.toFloat() / total * 360f
            if (sweep > gap) {
                drawArc(
                    color = if (i == 0) colors.accent else colors.inkFaint.copy(alpha = 0.35f),
                    startAngle = start + gap / 2,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = arc,
                    style = Stroke(stroke),
                )
            }
            start += sweep
        }
    }
}

private fun label(bin: SizeBin): String = when (val until = bin.until) {
    null -> "${bin.from.compact()}+"
    else -> if (bin.from.minor == 0L) "<${until.compact()}" else "${bin.from.compact()}–${until.compact()}"
}

private fun phrase(bin: SizeBin): String = when (val until = bin.until) {
    null -> "over ${bin.from.compact()}"
    else -> if (bin.from.minor == 0L) "under ${until.compact()}" else "${bin.from.compact()} to ${until.compact()}"
}

private fun monthName(m: Int): String =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m - 1]

/** A histogram dot's diameter and the gap between rows. */
private val DOT = 9.dp
private val DOT_GAP = 3.dp

/**
 * The month as a strip of days, one dot each: filled for a day money went out,
 * a hollow ring for a clear one. The clear-days page's picture, counted rather
 * than sized, because the page is about how many and not how much.
 */
@Composable
fun DayStrip(daily: List<Money>, hue: Int, modifier: Modifier = Modifier) {
    val colors = Vitt.colors
    // A week a row, so the strip reads as the month it is and fills the page
    // rather than sitting as a thin line across it.
    val perRow = 7
    val rows = (daily.size + perRow - 1) / perRow
    Canvas(modifier.fillMaxWidth().height(STRIP_ROW * rows)) {
        val step = size.width / perRow
        val rowStep = STRIP_ROW.toPx()
        val r = min(step, rowStep) / 2 - 4.dp.toPx()
        daily.forEachIndexed { i, day ->
            val c = Offset(step * (i % perRow) + step / 2, rowStep * (i / perRow) + rowStep / 2)
            if (day.minor == 0L) {
                drawCircle(colors.currency(hue), radius = r * 0.8f, center = c, style = Stroke(1.5.dp.toPx()))
            } else {
                drawCircle(colors.inkFaint.copy(alpha = 0.35f), radius = r * 0.55f, center = c)
            }
        }
    }
}

/**
 * The month's days as one line, highest day marked: the biggest-day page's
 * picture. A line, not bars, as the identity asks, and one hue.
 */
@Composable
fun DailyLine(daily: List<Money>, peak: Int?, hue: Int, modifier: Modifier = Modifier) {
    val colors = Vitt.colors
    val max = daily.maxOfOrNull { it.minor }?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        if (daily.size < 2) return@Canvas
        val stepX = size.width / (daily.size - 1)
        fun at(i: Int) = Offset(i * stepX, size.height - (daily[i].minor.toFloat() / max) * (size.height - 8.dp.toPx()))
        for (i in 1 until daily.size) {
            drawLine(colors.currency(hue).copy(alpha = 0.7f), at(i - 1), at(i), strokeWidth = 2.dp.toPx())
        }
        drawLine(colors.hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        peak?.let { drawCircle(colors.currency(hue), radius = 5.dp.toPx(), center = at(it)) }
    }
}

/** One dot a time: the regular merchant's page. Capped, with the count in words beside it. */
@Composable
fun CountDots(count: Int, hue: Int, modifier: Modifier = Modifier) {
    val colors = Vitt.colors
    val shown = count.coerceAtMost(35)
    val perRow = 7
    val rows = (shown + perRow - 1) / perRow
    Canvas(modifier.fillMaxWidth().height(STRIP_ROW * rows)) {
        val step = size.width / perRow
        val rowStep = STRIP_ROW.toPx()
        val r = min(step, rowStep) / 2 - 6.dp.toPx()
        (0 until shown).forEach { i ->
            drawCircle(colors.currency(hue), radius = r, center = Offset(step * (i % perRow) + step / 2, rowStep * (i / perRow) + rowStep / 2))
        }
    }
}

/**
 * Two amounts as two lines of proportional length, this month in the hue and
 * the comparison in neutral. Lines, never filled bars, and both in one
 * currency.
 */
@Composable
fun CompareLines(first: Money, firstLabel: String, second: Money, secondLabel: String, hue: Int, modifier: Modifier = Modifier) {
    val colors = Vitt.colors
    val max = maxOf(first.minor, second.minor).coerceAtLeast(1L)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vitt.space.loose)) {
        listOf(Triple(first, firstLabel, true), Triple(second, secondLabel, false)).forEach { (amount, label, ours) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
                Text(label, style = Vitt.type.body, color = colors.inkMuted, modifier = Modifier.fillMaxWidth(0.26f))
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    val w = size.width * (amount.minor.toFloat() / max)
                    drawLine(
                        if (ours) colors.currency(hue) else colors.inkFaint.copy(alpha = 0.5f),
                        Offset(0f, size.height / 2), Offset(w.coerceAtLeast(4.dp.toPx()), size.height / 2),
                        strokeWidth = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                Text(amount.compact(), style = Vitt.type.body, color = colors.ink)
            }
        }
    }
}

/**
 * Spent against the budget as a thin meter: the track is the budget, the hue
 * is what went out, and past the budget the line simply runs to the end with a
 * mark where the budget stood. A fact about the pocket, drawn the same way
 * whichever side of it the month landed.
 */
@Composable
fun BudgetMeter(spent: Money, budget: Money, hue: Int, modifier: Modifier = Modifier) {
    val colors = Vitt.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val y = size.height / 2
            val stroke = 10.dp.toPx()
            val scale = maxOf(spent.minor, budget.minor).coerceAtLeast(1L).toFloat()
            drawLine(colors.hairline, Offset(0f, y), Offset(size.width * (budget.minor / scale), y), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(colors.currency(hue), Offset(0f, y), Offset((size.width * (spent.minor / scale)).coerceAtLeast(stroke), y), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val mark = size.width * (budget.minor / scale)
            drawLine(colors.ink, Offset(mark, 0f), Offset(mark, size.height), strokeWidth = 1.5.dp.toPx())
        }
        Text("${spent.compact()} of ${budget.compact()}", style = Vitt.type.body, color = colors.inkMuted)
    }
}

/** One row of [DayStrip] and [CountDots]: a week, or seven visits. */
private val STRIP_ROW = 44.dp
