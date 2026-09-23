package ie.shoonya.vitt.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import ie.shoonya.vitt.model.Insights
import ie.shoonya.vitt.model.MonthReview
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import ie.shoonya.vitt.ui.screens.ReviewStory
import ie.shoonya.vitt.ui.theme.VittTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReviewStoryTest {

    private val aug = YearMonth(2026, 8)
    private val today = Civil.toDays(2026, 9, 3)
    private val all = (1..10).map { d ->
        Transaction(
            "t$d", Money(-(1000L * d), Currency.EUR), "TESCO", "groceries", null, null,
            Civil.toDays(2026, 8, d), null, emptySet(), Money(0, Currency.EUR), null, false,
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.story(onDone: () -> Unit) {
        val review = MonthReview.of(all, Currency.EUR, aug, today, budget = Money(80000, Currency.EUR))!!
        setContent {
            VittTheme {
                ReviewStory(
                    review = review,
                    daily = Insights.dailyTotals(all, Currency.EUR, aug, aug.lastDay),
                    categories = Insights.foldTail(Insights.byCategory(all, Currency.EUR, aug)),
                    hue = 0,
                    today = today,
                    onDone = onDone,
                )
            }
        }
    }

    @Test
    fun `a tap moves the story on and the last page closes it`() = runComposeUiTest {
        var done = false
        story { done = true }

        onNodeWithText("entries in August", substring = true).assertExists()
        onNodeWithContentDescription("Page 1 of", substring = true).performClick()
        waitForIdle()
        onNodeWithText("100% of what went out", substring = true).assertExists()

        // One tap a page, each on the page now showing, to the end.
        var page = 2
        while (!done && page <= 10) {
            onNodeWithContentDescription("Page $page of", substring = true).performClick()
            waitForIdle()
            page++
        }
        onNodeWithText("That was August").assertExists()
        assertTrue(done, "the last page's tap closes the story")
    }

    /** Each page at phone size, left in build/layout-shots to look at. */
    @Test
    fun `every page fills a phone`() = runDesktopComposeUiTest(390, 844) {
        var done = false
        story { done = true }
        val out = File("build/layout-shots").apply { mkdirs() }
        var page = 1
        while (!done && page <= 10) {
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "story-$page.png"))
            onNodeWithContentDescription("Page $page of", substring = true).performClick()
            page++
        }
        assertTrue(done)
    }

    @Test
    fun `no budget means no budget page`() = runComposeUiTest {
        val review = MonthReview.of(all, Currency.EUR, aug, today)!!
        setContent {
            VittTheme {
                ReviewStory(review, Insights.dailyTotals(all, Currency.EUR, aug, aug.lastDay), emptyList(), 0, today, {})
            }
        }
        onNodeWithText("left of the budget", substring = true).assertDoesNotExist()
        onNodeWithText("past the budget", substring = true).assertDoesNotExist()
    }
}
