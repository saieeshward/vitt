package ie.shoonya.vitt.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ie.shoonya.vitt.VittServices
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.db.VittDatabase
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The whole app, with sample data, at the window sizes it has to work at: a
 * phone upright and on its side, an iPad upright and across. Each run leaves a
 * screenshot in build/layout-shots for looking at, which is the check that
 * matters; the assertions only catch the layout rule being wired wrong.
 */
@OptIn(ExperimentalTestApi::class)
class LayoutSizesTest {

    private fun services(): VittServices {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { VittDatabase.Schema.create(it) }
        // A fixed day, so a screenshot taken tomorrow looks like one taken today.
        return VittServices(browser = BrowserAuth(), now = { 1_789_000_000_000L }, driver = driver)
            .also { it.seedSampleData() }
    }

    private fun shot(
        name: String,
        width: Int,
        height: Int,
        expectRail: Boolean,
        then: (androidx.compose.ui.test.ComposeUiTest.(save: (String) -> Unit) -> Unit)? = null,
    ) = runDesktopComposeUiTest(width, height) {
        setContent { AppRoot(services()) }
        waitForIdle()
        // One Add button either way: in the bar on a phone, at the top of
        // the rail otherwise.
        assertEquals(1, onAllNodesWithContentDescription("Add transaction").fetchSemanticsNodes().size)
        val out = File("build/layout-shots").apply { mkdirs() }
        val save: (String) -> Unit = { suffix ->
            waitForIdle()
            // The topmost root: a sheet or a dialog is a layer of its own.
            val top = onAllNodes(isRoot()).onLast()
            ImageIO.write(top.captureToImage().toAwtImage(), "png", File(out, "$name$suffix.png"))
        }
        save("")
        assertEquals(expectRail, ie.shoonya.vitt.layout.WindowLayout(width, height).useRail)
        then?.invoke(this, save)
    }

    @Test fun `phone upright keeps the bottom bar`() = shot("phone", 390, 844, expectRail = false)

    @Test fun `phone on its side moves to the rail — and Add goes two-pane`() =
        shot("phone-landscape", 844, 390, expectRail = true) { save ->
            onNodeWithContentDescription("Add transaction").performClick()
            save("-add")
        }

    @Test fun `iPad upright gets the rail — and Add is a dialog`() =
        shot("ipad", 820, 1180, expectRail = true) { save ->
            onNodeWithContentDescription("Add transaction").performClick()
            save("-add")
        }

    @Test fun `iPad across gets the rail and Reports in two columns`() =
        shot("ipad-landscape", 1366, 1024, expectRail = true) { save ->
            onNodeWithText("Reports").performClick()
            save("-reports")
        }
}
