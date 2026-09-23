package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ie.shoonya.vitt.layout.WindowLayout

/** The layout this window gets. Provided once, at the root; nothing reads the device type. */
val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.PHONE }

/** Works out [LocalWindowLayout] from the window as it is now, so a resize or a rotation re-lays out. */
@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    val size = LocalWindowInfo.current.containerSize
    val layout = with(LocalDensity.current) { WindowLayout(size.width.toDp().value.toInt(), size.height.toDp().value.toInt()) }
    CompositionLocalProvider(LocalWindowLayout provides layout, content = content)
}

/**
 * Where a task sheet appears: sliding up from the bottom on a phone, and as a
 * centred dialog on a window wide and tall enough, where a full-width sheet is
 * a phone gesture scaled up. The content is the same either way and stays
 * modal, because the app never saves on arrival and a half-typed entry should
 * not be left behind a click. `docs/ipad-plan.clan`, D4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    containerColor: Color,
    sheetGesturesEnabled: Boolean,
    dragHandle: (@Composable () -> Unit)?,
    /**
     * A standing panel on the trailing edge instead, for editing one entry of
     * a list that stays in view and in reach beside it: no scrim, and a tap on
     * another row changes what the panel shows.
     */
    side: Boolean = false,
    content: @Composable () -> Unit,
) {
    val layout = LocalWindowLayout.current
    if (side) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                modifier = Modifier
                    .width(SIDE_PANEL_WIDTH)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
                color = containerColor,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            ) { content() }
        }
    } else if (layout.dialogSheets) {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = DIALOG_WIDTH).heightIn(max = (layout.heightDp * 0.86f).dp),
                    shape = RoundedCornerShape(24.dp),
                    color = containerColor,
                ) { content() }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            sheetGesturesEnabled = sheetGesturesEnabled,
            dragHandle = dragHandle,
        ) { content() }
    }
}

/**
 * The most a sheet's scrolling body may take: the height left once the status
 * bar, the home indicator and [reserve] (the sheet's handle, header, padding
 * and anything pinned below the body) are taken off. A share of the whole
 * window, as it was, left the foot of every sheet empty while its body
 * scrolled, and once the app drew edge to edge ran the body under the home
 * indicator.
 */
@Composable
fun sheetBodyMaxHeight(reserve: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val usable = LocalWindowInfo.current.containerSize.height - insets.getTop(density) - insets.getBottom(density)
    return with(density) { (usable.toDp() - reserve).coerceAtLeast(160.dp) }
}

/** A sheet's own chrome: handle, header row, padding and the gaps between them. */
val SHEET_CHROME = 176.dp

/** The pinned part keypad: its label row and four rows of keys over the amount. */
val PART_KEYPAD = 360.dp

/** A task dialog's width: a comfortable form, not a page. */
private val DIALOG_WIDTH = 560.dp

/** The side panel's width, which the list beside it gives up while it is open. */
val SIDE_PANEL_WIDTH = 420.dp
