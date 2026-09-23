package ie.shoonya.vitt.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.runComposeUiTest
import ie.shoonya.vitt.model.Account
import ie.shoonya.vitt.model.AccountKind
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.screens.AddScreen
import ie.shoonya.vitt.ui.screens.NewEntry
import ie.shoonya.vitt.ui.theme.VittTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Add sheet, driven the way a person drives it: by the keys and chips, found
 * by the names VoiceOver reads out. RecordContractTest pins the seam between
 * what this sheet can produce and what `record()` accepts; this pins the sheet.
 *
 * The bugs that mattered here were found by using the app, not by reading it:
 * a keypad that refused every digit, a chip whose tap target missed. A test at
 * this level is the nearest thing to that which runs without a phone.
 */
@OptIn(ExperimentalTestApi::class)
class AddFlowTest {

    private val aib = Account("a1", "AIB", Currency.EUR, AccountKind.CURRENT, Money(0, Currency.EUR), archived = false, deleted = false)
    private val revolut = Account("a2", "Revolut", Currency.EUR, AccountKind.CURRENT, Money(0, Currency.EUR), archived = false, deleted = false)

    private fun androidx.compose.ui.test.ComposeUiTest.sheet(
        onSave: (NewEntry) -> Unit,
        error: String? = null,
        lastAccountId: String? = "a1",
        usuals: List<ie.shoonya.vitt.model.Usual> = emptyList(),
    ) = setContent {
        VittTheme {
            AddScreen(
                currencies = listOf(Currency.EUR),
                accounts = listOf(aib, revolut),
                frequentSpending = emptyList(),
                frequentIncome = emptyList(),
                lastAccountId = lastAccountId,
                onSave = onSave,
                onCancel = {},
                error = error,
                usuals = usuals,
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
    fun `typing an amount and saving records an expense against the last account`() = runComposeUiTest {
        var saved: NewEntry? = null
        sheet(onSave = { saved = it })

        type("1", "2", "Decimal point", "5", "0")
        onNodeWithText("Save").performClick()

        // Negative: the keypad holds a magnitude and Expense gives the sign.
        assertEquals(Money(-1250, Currency.EUR), saved?.amount)
        assertEquals("a1", saved?.accountId)
    }

    @Test
    fun `save waits for an amount`() = runComposeUiTest {
        // A zero row can never be taken back from an append-only log.
        sheet(onSave = {})
        onNodeWithText("Save").assertIsNotEnabled()
        type("4")
        onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `income is saved positive`() = runComposeUiTest {
        var saved: NewEntry? = null
        sheet(onSave = { saved = it })

        onNodeWithText("Income").performClick()
        type("4", "0")
        onNodeWithText("Save").performClick()

        assertEquals(Money(4000, Currency.EUR), saved?.amount)
    }

    @Test
    fun `another account is one tap`() = runComposeUiTest {
        var saved: NewEntry? = null
        sheet(onSave = { saved = it })

        onNodeWithText("Revolut").performClick()
        type("9")
        onNodeWithText("Save").performClick()

        assertEquals("a2", saved?.accountId)
    }

    @Test
    fun `a stray tap that clears the account is said out loud and still saves`() = runComposeUiTest {
        // Capture often cannot tell which account paid, so a blank is allowed,
        // but the chips are a toggle and a blank has to be visible.
        var saved: NewEntry? = null
        sheet(onSave = { saved = it })

        onNodeWithText("AIB").performClick()
        onNodeWithText("No account picked", substring = true).assertExists()
        type("3")
        onNodeWithText("Save").performClick()

        assertNull(saved?.accountId)
    }

    @Test
    fun `a refused save keeps the figure on screen with the reason`() = runComposeUiTest {
        // The sheet closing on a transaction that was never written is the one
        // failure a money app does not get to have.
        sheet(onSave = {}, error = "That account holds a different currency.")
        onNodeWithText("That account holds a different currency.").assertExists()
        onNodeWithText("Save").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.splitSheet(onSave: (NewEntry) -> Unit, known: List<String>) =
        setContent {
            VittTheme {
                AddScreen(
                    currencies = listOf(Currency.EUR),
                    accounts = listOf(aib),
                    frequentSpending = emptyList(),
                    frequentIncome = emptyList(),
                    lastAccountId = "a1",
                    knownPeople = known,
                    onSave = onSave,
                    onCancel = {},
                )
            }
        }

    @Test
    fun `a split is the bill then a tick for each person — equal with no typing`() = runComposeUiTest {
        // Splitwise's and Tricount's shape: who it was for, on the same screen,
        // with each part shown as they are ticked.
        var saved: NewEntry? = null
        splitSheet(onSave = { saved = it }, known = listOf("bea", "cal"))

        onNodeWithText("Split this").performScrollTo().performClick()
        type("6", "0")
        onNodeWithText("Next").performClick()
        onNodeWithText("bea").performClick()
        onNodeWithText("cal").performClick()
        onNodeWithContentDescription("bea's part €20.00. Change").assertExists()
        onNodeWithText("Save").performClick()

        assertEquals(Money(-2000, Currency.EUR), saved?.amount)
        assertEquals(Money(-6000, Currency.EUR), saved?.totalPaid)
        assertEquals(setOf("bea", "cal"), saved?.splitWith)
        assertNull(saved?.shares, "equal is not stored")
    }

    @Test
    fun `typing one part leaves it and the untouched rows share the rest`() = runComposeUiTest {
        var saved: NewEntry? = null
        splitSheet(onSave = { saved = it }, known = listOf("bea", "cal"))

        onNodeWithText("Split this").performScrollTo().performClick()
        type("6", "0")
        onNodeWithText("Next").performClick()
        onNodeWithText("bea").performClick()
        onNodeWithText("cal").performClick()
        onNodeWithContentDescription("bea's part €20.00. Change").performClick()
        type("3", "0")
        // Live, with no Set: you and Cal now share the other €30.
        onNodeWithContentDescription("cal's part €15.00. Change").assertExists()
        onNodeWithText("Save").performClick()

        assertEquals(Money(-1500, Currency.EUR), saved?.amount)
        assertEquals(mapOf("bea" to Money(3000, Currency.EUR), "cal" to Money(1500, Currency.EUR)), saved?.shares)
    }

    @Test
    fun `parts past the bill say so and Save waits`() = runComposeUiTest {
        splitSheet(onSave = {}, known = listOf("bea"))

        onNodeWithText("Split this").performScrollTo().performClick()
        type("2", "0")
        onNodeWithText("Next").performClick()
        onNodeWithText("bea").performClick()
        onNodeWithContentDescription("bea's part €10.00. Change").performClick()
        type("2", "5")
        onNodeWithText("more than the bill", substring = true).assertExists()
        onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `somebody new is named on a step of their own`() = runComposeUiTest {
        var saved: NewEntry? = null
        splitSheet(onSave = { saved = it }, known = emptyList())

        onNodeWithText("Split this").performScrollTo().performClick()
        type("4", "0")
        onNodeWithText("Next").performClick()
        onNodeWithText("+ Someone new").performClick()
        onNode(hasSetTextAction()).performTextInput("Dev")
        onNodeWithText("Add").performClick()
        onNodeWithText("Save").performClick()

        assertEquals(setOf("dev"), saved?.splitWith)
        assertEquals(Money(-2000, Currency.EUR), saved?.amount)
    }

    @Test
    fun `a hardware keyboard types the amount and Return saves it`() = runComposeUiTest {
        // An iPad with a Magic Keyboard: key presses into the drawn keypad,
        // never the system keyboard.
        var saved: NewEntry? = null
        sheet(onSave = { saved = it })
        waitForIdle()

        onRoot().performKeyInput {
            pressKey(Key.Four)
            pressKey(Key.Two)
            pressKey(Key.Period)
            pressKey(Key.Five)
            pressKey(Key.Enter)
        }

        assertEquals(Money(-4250, Currency.EUR), saved?.amount)
    }

    @Test
    fun `backspace on a keyboard takes a digit off`() = runComposeUiTest {
        var saved: NewEntry? = null
        sheet(onSave = { saved = it })
        waitForIdle()

        onRoot().performKeyInput {
            pressKey(Key.Nine)
            pressKey(Key.Nine)
            pressKey(Key.Backspace)
            pressKey(Key.Enter)
        }

        assertEquals(Money(-900, Currency.EUR), saved?.amount)
    }

    @Test
    fun `a usual is one tap and Save — the whole entry filled in`() = runComposeUiTest {
        var saved: NewEntry? = null
        val coffee = ie.shoonya.vitt.model.Usual(
            "Coffee", Money(-450, Currency.EUR), ie.shoonya.vitt.capture.Category.ofCode("dining"), "a2", count = 5,
        )
        sheet(onSave = { saved = it }, usuals = listOf(coffee))

        onNodeWithText("Coffee €4.50", substring = true).performClick()
        onNodeWithText("Save").performClick()

        assertEquals(Money(-450, Currency.EUR), saved?.amount)
        assertEquals("a2", saved?.accountId)
        assertEquals("dining", saved?.category?.code)
        assertEquals("Coffee", saved?.note)
    }
}
