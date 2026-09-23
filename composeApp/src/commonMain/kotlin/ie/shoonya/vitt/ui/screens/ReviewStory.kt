package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.shoonya.vitt.model.CategorySlice
import ie.shoonya.vitt.model.MonthReview
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.ui.CategoryRing
import ie.shoonya.vitt.ui.DayDots
import ie.shoonya.vitt.ui.theme.Vitt
import kotlinx.coroutines.launch

/**
 * The card in Reports that opens a month's story. Worded as an offer, and only
 * there when there is a month worth telling (see [MonthReview.MIN_ENTRIES]).
 */
@Composable
fun ReviewCard(review: MonthReview, hue: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Vitt.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
            .padding(Vitt.space.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        // A glow in the currency's hue: the one colour this card is allowed.
        Box(Modifier.size(10.dp).clip(CircleShape).background(colors.currency(hue)))
        Column(Modifier.weight(1f)) {
            Text(title(review), style = Vitt.type.body, color = colors.ink)
            Text(
                "${review.entries} entries on ${review.daysRecorded} days",
                style = Vitt.type.label,
                color = colors.inkMuted,
            )
        }
        // The button's role already says it opens; VoiceOver reading "›" aloud adds nothing.
        Text("›", style = Vitt.type.title, color = colors.inkMuted, modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * The month told back, one fact a page, swiped like a story.
 *
 * Pages without anything to say are left out rather than shown empty: no
 * category page for a month of uncategorised entries, no budget page without a
 * budget. The last page says what the month was and stops; there is no score.
 */
@Composable
fun ReviewStory(
    review: MonthReview,
    daily: List<Money>,
    categories: List<CategorySlice>,
    hue: Int,
    today: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Vitt.colors
    val name = monthName(review.month.month)
    val pages = buildList<@Composable () -> Unit> {
        add {
            Page(
                big = "${review.entries}",
                line = "entries in $name, on ${review.daysRecorded} ${plural(review.daysRecorded, "day")}.",
            ) { DayDots(review.month, daily, if (review.complete) review.month.lastDay else today, hue, interactive = false) }
        }
        review.topCategory?.let { top ->
            add {
                val rest = review.uncategorised.takeIf { it.minor > top.spent.minor }
                    ?.let { " ${it.displayUnsigned()} has no category yet." }.orEmpty()
                Page(big = top.label, line = "${review.topShare}% of what went out: ${top.spent.displayUnsigned()}.$rest") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CategoryRing(
                            categories, review.spent, hue,
                            focused = categories.indexOf(top).coerceAtLeast(0),
                            onFocus = {},
                            interactive = false,
                            diameter = 240.dp,
                        )
                    }
                }
            }
        }
        if (review.clearDays > 0) {
            add {
                Page(
                    big = "${review.clearDays}",
                    line = "${plural(review.clearDays, "day")} with nothing going out, of ${review.daysCounted}.",
                ) { ie.shoonya.vitt.ui.DayStrip(daily, hue) }
            }
        }
        review.biggestDay?.let { (day, amount) ->
            add {
                val (_, _, d) = Civil.fromDays(day)
                Page(big = amount.displayUnsigned(), line = "went out on ${weekday(day)} $d $name, the most of any day.") {
                    ie.shoonya.vitt.ui.DailyLine(daily, peak = day - review.month.firstDay, hue = hue)
                }
            }
        }
        review.regular?.let { m ->
            add {
                Page(big = m.label, line = "${m.count} times, ${m.spent.displayUnsigned()} in all.") {
                    ie.shoonya.vitt.ui.CountDots(m.count, hue)
                }
            }
        }
        review.versusLast?.let { delta ->
            val last = monthName(review.month.previous().month)
            add {
                val lines: @Composable () -> Unit = {
                    ie.shoonya.vitt.ui.CompareLines(review.spent, name, review.spent - delta, last, hue)
                }
                when {
                    review.aboutTheSame -> Page(big = "About the same", line = "as $last.", visual = lines)
                    delta.minor < 0 -> Page(big = (-delta).displayUnsigned(), line = "less went out than in $last.", visual = lines)
                    else -> Page(big = delta.displayUnsigned(), line = "more went out than in $last.", visual = lines)
                }
            }
        }
        review.room?.let { room ->
            add {
                Page(big = room.displayUnsigned(), line = if (review.complete) "left of the budget." else "left of the budget so far.") {
                    ie.shoonya.vitt.ui.BudgetMeter(review.spent, review.budget!!, hue)
                }
            }
        }
        review.past?.let { past ->
            add {
                Page(big = past.displayUnsigned(), line = "past the budget of ${review.budget!!.displayUnsigned()}.") {
                    ie.shoonya.vitt.ui.BudgetMeter(review.spent, review.budget!!, hue)
                }
            }
        }
        add {
            Page(big = if (review.complete) "That was $name" else "$name so far", line = "${review.spent.displayUnsigned()} out, ${review.received.displayUnsigned()} in.") {
                ie.shoonya.vitt.ui.CompareLines(review.spent, "Out", review.received, "In", hue)
            }
        }
    }

    val state = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    // The whole sheet, header at the top and the page dots at the foot, each
    // page centred in what is between: a story, not a list stopping halfway
    // down an empty screen.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(ie.shoonya.vitt.ui.sheetBodyMaxHeight(STORY_CHROME))
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title(review), style = Vitt.type.title, color = colors.ink)
            TextButton(onClick = onDone) { Text("Done") }
        }
        HorizontalPager(state = state, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            // A tap moves on, as a story does, and the last page's tap closes
            // it. Swiping works too, back as well as forward.
            Box(
                Modifier
                    .fillMaxSize()
                    // No ripple or hover wash: the whole page is the target, and
                    // a page-sized grey flash reads as a glitch, not a press.
                    .clickable(interactionSource = null, indication = null) { scope.launch { if (page < pages.lastIndex) state.animateScrollToPage(page + 1) else onDone() } }
                    .semantics { contentDescription = "Page ${page + 1} of ${pages.size}" },
            ) { pages[page]() }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            repeat(pages.size) { i ->
                Box(
                    Modifier.size(if (i == state.currentPage) 8.dp else 6.dp).clip(CircleShape)
                        .background(colors.currency(hue).copy(alpha = if (i == state.currentPage) 1f else 0.3f)),
                )
            }
        }
    }
}

@Composable
private fun Page(big: String, line: String, visual: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.section, Alignment.CenterVertically),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
            Text(big, style = Vitt.type.display.copy(fontSize = 44.sp, lineHeight = 48.sp), color = Vitt.colors.ink, maxLines = 2)
            Text(line, style = Vitt.type.body, color = Vitt.colors.inkMuted)
        }
        visual?.invoke()
    }
}

private fun title(review: MonthReview): String {
    val name = monthName(review.month.month)
    return if (review.complete) "$name in review" else "$name so far"
}

private fun plural(n: Int, word: String) = if (n == 1) word else "${word}s"

private fun monthName(m: Int): String =
    listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[m - 1]

private fun weekday(day: Int): String =
    listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[Civil.dayOfWeek(day)]

/** The story's sheet handle and outer padding, which the story fills the rest of. */
private val STORY_CHROME = 64.dp
