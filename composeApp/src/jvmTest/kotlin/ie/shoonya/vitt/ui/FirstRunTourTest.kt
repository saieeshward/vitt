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
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ie.shoonya.vitt.VittServices
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.db.VittDatabase
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * A fresh install walked the way a new person walks it, at iPhone size: setup,
 * the first home screen, every tab while empty, the first entry, Settings. It
 * asserts only that each step can be reached; the point is the screenshots in
 * build/first-run, which are what a first-experience review looks at.
 */
@OptIn(ExperimentalTestApi::class)
class FirstRunTourTest {

    @Test
    fun `a fresh install can be walked from setup to a first entry`() = runDesktopComposeUiTest(393, 852) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { VittDatabase.Schema.create(it) }
        val services = VittServices(browser = BrowserAuth(), now = { 1_789_000_000_000L }, driver = driver)
        setContent { AppRoot(services) }
        val out = File("build/first-run").apply { deleteRecursively(); mkdirs() }
        var n = 0
        fun shot(name: String) {
            waitForIdle()
            val top = onAllNodes(isRoot()).onLast()
            ImageIO.write(top.captureToImage().toAwtImage(), "png", File(out, "%02d-%s.png".format(++n, name)))
        }

        shot("setup-empty")
        onNodeWithText("AIB").performClick()
        onNodeWithText("Revolut").performClick()
        shot("setup-two-picked")
        onNodeWithText("Next", substring = true).performClick()
        shot("balances")
        onAllNodesWithText("Add").onFirst().performClick()
        shot("balance-keypad")
        listOf("1", "2", "0", "0").forEach { onNodeWithContentDescription(it).performClick() }
        onNodeWithText("Save").performClick()
        shot("balances-one-filled")
        onNodeWithText("Start with", substring = true).performClick()
        // Setup lands on home, where the welcome card offers the first entry.
        onNodeWithText("Log something").assertExists()
        shot("home-first")

        for (tab in listOf("Activity", "People", "Reports")) {
            onAllNodes(hasText(tab) and hasClickAction()).onFirst().performClick()
            shot("tab-${tab.lowercase()}-empty")
        }
        onAllNodes(hasText("Ledgers") and hasClickAction()).onFirst().performClick()

        onNodeWithText("Log something").performClick()
        shot("add-first")
        listOf("4", "Decimal point", "5", "0").forEach { onNodeWithContentDescription(it).performClick() }
        shot("add-typed")
        onNodeWithText("Save").performClick()
        // The receipt says what was saved and where, with a way back.
        onNodeWithText("Saved €4.50 from AIB.").assertExists()
        onNodeWithText("Undo").assertExists()
        shot("home-after-first-entry")
        onAllNodes(hasText("Activity") and hasClickAction()).onFirst().performClick()
        shot("activity-after-first-entry")
        onAllNodes(hasText("Ledgers") and hasClickAction()).onFirst().performClick()
        onNodeWithContentDescription("Settings").performClick()
        shot("settings")
    }
}
