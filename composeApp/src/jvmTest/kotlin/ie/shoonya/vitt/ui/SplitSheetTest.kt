package ie.shoonya.vitt.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.screens.SplitSheet
import ie.shoonya.vitt.ui.theme.VittTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The split editor, driven through its figures and its keypad. */
@OptIn(ExperimentalTestApi::class)
class SplitSheetTest {

    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    /** €60 three ways, equal, nothing paid back. */
    private val dinner = Transaction(
        id = "t1", amount = eur(-2000), merchant = "Pizzeria", category = null, categorySource = null,
        accountId = null, day = 20_000, totalPaid = eur(-6000), splitWith = setOf("bea", "cal"),
        settled = eur(0), note = null, deleted = false,
    )

    private class Calls {
        var edited: List<Any?>? = null
        var settled: Pair<String, Money>? = null
    }

    private fun androidx.compose.ui.test.ComposeUiTest.sheet(calls: Calls, split: Transaction = dinner) = setContent {
        VittTheme {
            SplitSheet(
                split = split,
                knownPeople = listOf("bea", "cal", "dev"),
                onSettle = {},
                onSettleInFull = {},
                onSettlePerson = { who, m -> calls.settled = who to m },
                onEditSplit = { t, y, o, p -> calls.edited = listOf(t, y, o, p); null },
                onDone = {},
            )
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.type(vararg keys: String) =
        keys.forEach { key ->
            // The main keypad sits in the sheet's scroll; the part keypad is
            // pinned below it and has no scroll to be brought into.
            val node = onNodeWithContentDescription(key)
            runCatching { node.performScrollTo() }
            node.performClick()
        }

    @Test
    fun `typing one part saves it with the rest shared by the untouched rows`() = runComposeUiTest {
        val calls = Calls()
        sheet(calls)

        onNodeWithContentDescription("bea's part €20.00. Change").performClick()
        type("3", "0")
        onNodeWithText("Save").performClick()

        assertEquals(
            listOf(eur(6000), eur(1500), mapOf("bea" to eur(3000), "cal" to eur(1500)), setOf("bea", "cal")),
            calls.edited,
        )
    }

    @Test
    fun `ticking somebody in re-splits and saves them with the others`() = runComposeUiTest {
        val calls = Calls()
        sheet(calls)

        onNodeWithText("dev").performClick()
        onNodeWithContentDescription("dev's part €15.00. Change").assertExists()
        onNodeWithText("Save").performClick()

        assertEquals(setOf("bea", "cal", "dev"), calls.edited!![3])
        assertNull(calls.edited!![2], "still equal, so nothing stored per person")
    }

    @Test
    fun `split equally undoes typed parts`() = runComposeUiTest {
        val calls = Calls()
        sheet(calls)

        onNodeWithContentDescription("bea's part €20.00. Change").performClick()
        type("3", "0")
        onNodeWithText("Split equally").performScrollTo().performClick()

        onNodeWithText("Settled up").assertExists()
        assertNull(calls.edited)
    }

    @Test
    fun `one person paying back is recorded against them alone`() = runComposeUiTest {
        val calls = Calls()
        sheet(calls)

        onNodeWithContentDescription("bea paid back in full").performClick()

        assertEquals("bea" to eur(2000), calls.settled)
    }
}
