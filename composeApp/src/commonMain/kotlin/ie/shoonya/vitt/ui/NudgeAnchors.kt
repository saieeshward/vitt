package ie.shoonya.vitt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * The places on screen the companion can walk to.
 *
 * Controls that a nudge can point at register their bounds here as they lay
 * out, and the companion layer reads them. Root coordinates, so the layer can
 * compare them against its own without knowing where either sits in the tree.
 * A map rather than a list of callbacks because a control can leave the screen
 * (the Habit tab when the switch is off) and its entry must go stale rather
 * than send her to where it used to be.
 */
typealias NudgeAnchors = SnapshotStateMap<String, Rect>

val LocalNudgeAnchors = compositionLocalOf<NudgeAnchors> { mutableStateMapOf() }

@Composable
fun rememberNudgeAnchors(): NudgeAnchors = remember { mutableStateMapOf() }

/** Marks this control as somewhere the companion may go and stand. */
@Composable
fun Modifier.nudgeAnchor(key: String): Modifier {
    val anchors = LocalNudgeAnchors.current
    return onGloballyPositioned { anchors[key] = it.boundsInRoot() }
}

/** Anchor keys. Strings rather than an enum so a screen can register a per-currency card. */
object Anchor {
    const val TAB_ACTIVITY = "tab.activity"
    const val TAB_PEOPLE = "tab.people"
    const val ADD = "add"
    const val SETTINGS = "settings"
    fun ledgerCard(code: String) = "ledger.$code"
}
