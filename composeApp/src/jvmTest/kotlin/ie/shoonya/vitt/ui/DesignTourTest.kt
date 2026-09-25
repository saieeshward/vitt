package ie.shoonya.vitt.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ie.shoonya.vitt.VittServices
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.db.VittDatabase
import ie.shoonya.vitt.model.Choice
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The app with a month of sample data, in light mode, screen by screen at
 * iPhone size: every tab, the Add sheet, the split step, a category edit,
 * Settings and the month story. Asserts only that each opens; the point is
 * the screenshots in build/design-tour, which is what a design review reads.
 */
@OptIn(ExperimentalTestApi::class)
class DesignTourTest {

    @Test
    fun `every main screen opens in light mode with sample data`() = runDesktopComposeUiTest(393, 852) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { VittDatabase.Schema.create(it) }
        val services = VittServices(browser = BrowserAuth(), now = { 1_789_000_000_000L }, driver = driver)
        services.seedSampleData()
        // Light, whatever the machine running the test prefers.
        services.ledger.setChoice(Choice.APPEARANCE, "light")
        setContent { AppRoot(services) }
        val out = File("build/design-tour").apply { deleteRecursively(); mkdirs() }
        var n = 0
        fun shot(name: String) {
            waitForIdle()
            val top = onAllNodes(isRoot()).onLast()
            ImageIO.write(top.captureToImage().toAwtImage(), "png", File(out, "%02d-%s.png".format(++n, name)))
        }
        fun tab(name: String) = onAllNodes(hasText(name) and hasClickAction()).onFirst().performClick()

        shot("ledgers")
        tab("Activity"); shot("activity")
        onAllNodesWithText("Coffee Angel", substring = true).onFirst().performClick(); shot("edit-category")
        onNodeWithText("Done").performClick()
        tab("People"); shot("people")
        tab("Reports"); shot("reports")
        onNodeWithText("September so far", substring = true).performClick(); shot("story-1")
        onNodeWithContentDescription("Page 1 of", substring = true).performClick(); shot("story-2")
        onNodeWithText("Done").performClick()
        tab("Ledgers")
        onNodeWithContentDescription("Add transaction").performClick(); shot("add")
        listOf("6", "0").forEach { onNodeWithContentDescription(it).performClick() }
        onNodeWithText("Split this").also { runCatching { it.performScrollTo() } }.performClick()
        onNodeWithText("Next").performClick(); shot("split")
        onNodeWithText("Back").performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithContentDescription("Settings").performClick(); shot("settings")
    }
}
