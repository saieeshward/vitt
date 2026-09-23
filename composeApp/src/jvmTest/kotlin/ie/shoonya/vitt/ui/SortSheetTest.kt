package ie.shoonya.vitt.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.ui.screens.SortSheet
import ie.shoonya.vitt.ui.theme.VittTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SortSheetTest {

    private fun t(id: String, minor: Long, merchant: String) = Transaction(
        id, Money(minor, Currency.EUR), merchant, null, null, null,
        Civil.toDays(2026, 9, 10), null, emptySet(), Money(0, Currency.EUR), null, false,
    )

    @Test
    fun `a tap files the entry and the next is already showing`() = runComposeUiTest {
        val picked = mutableListOf<Pair<String, Category>>()
        setContent {
            var queue by remember { mutableStateOf(listOf(t("a", -1200, "TESCO"), t("b", -800, "LUAS"))) }
            VittTheme {
                SortSheet(
                    queue = queue,
                    frequentSpending = emptyList(),
                    frequentIncome = emptyList(),
                    onPick = { id, c -> picked += id to c; queue = queue.filterNot { it.id == id } },
                    onDone = {},
                )
            }
        }
        onNodeWithText("2 to sort").assertExists()
        onNodeWithText("Groceries").performClick()
        onNodeWithText("1 to sort").assertExists()
        onNodeWithText("Luas", substring = true).assertExists()
        onNodeWithText("Skip").performClick()
        onNodeWithText("Sorted").assertExists()
        assertEquals(listOf("a" to Category.ofCode("groceries")!!), picked)
    }

    @Test
    fun `sorting the last one closes the sheet`() = runComposeUiTest {
        var done = false
        setContent {
            var queue by remember { mutableStateOf(listOf(t("a", -1200, "TESCO"))) }
            VittTheme {
                SortSheet(queue, emptyList(), emptyList(), onPick = { id, _ -> queue = queue.filterNot { it.id == id } }, onDone = { done = true })
            }
        }
        onNodeWithText("Groceries").performClick()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        kotlin.test.assertTrue(done)
    }
}
