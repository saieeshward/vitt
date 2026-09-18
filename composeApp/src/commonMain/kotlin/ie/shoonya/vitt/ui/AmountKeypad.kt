package ie.shoonya.vitt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import ie.shoonya.vitt.ui.theme.Vitt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.shoonya.vitt.money.AmountEntry

/**
 * A drawn numeric keypad for entering an amount.
 *
 * The system keyboard is never summoned. That is the point: text input is
 * Compose Multiplatform's weakest area on iOS, and amount entry is the single
 * most-used interaction in this app, so the two are kept apart. Every key here
 * is an ordinary Composable with a click handler, which behaves identically on
 * both platforms.
 *
 * It is also better for the job than a system keyboard would be — large targets,
 * no letters, no autocorrect, and no way to type a second decimal point.
 */
@Composable
fun AmountKeypad(
    entry: AmountEntry,
    onEntryChange: (AmountEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AmountDisplay(entry)

        // The bottom row is the one every money app shares: point, zero,
        // delete. A currency with no minor units (yen) gets a blank where the
        // point would be, so the layout never shifts between currencies.
        val point = if (entry.currency.exponent > 0) "." else ""
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(point, "0", "⌫"),
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { label ->
                    KeypadKey(
                        label = label,
                        modifier = Modifier.weight(1f).fillMaxSize().padding(vertical = 4.dp),
                        onClick = {
                            onEntryChange(
                                when (label) {
                                    "" -> entry
                                    "⌫" -> entry.backspace()
                                    else -> entry.press(label.first())
                                }
                            )
                        },
                        // A long press on delete clears the lot, which is what
                        // the old C key did; it earned its own key less than
                        // the decimal point did.
                        onLongClick = if (label == "⌫") ({ onEntryChange(entry.clear()) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountDisplay(entry: AmountEntry) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            // One node, not two: with a plain description on the Box and a
            // Text child, a screen reader announced the amount twice. The
            // directional isolates around an Arabic symbol are stripped, since
            // some engines read them aloud.
            .clearAndSetSemantics {
                contentDescription = "Amount " + entry.display().filterNot { it == '\u2068' || it == '\u2069' }
            },
        contentAlignment = Alignment.Center,
    ) {
        // One line always. Twelve whole digits or a large system font would
        // otherwise wrap after a comma and push the keypad down mid-entry, so
        // the figure shrinks to fit instead.
        BasicText(
            text = entry.display(),
            style = Vitt.type.moneyHero.copy(
                // A zero placeholder is faint, not grey-as-failure: absence is
                // styled as absence everywhere in this app.
                color = if (entry.isEmpty) Vitt.colors.inkFaint else Vitt.colors.ink,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 34.sp, stepSize = 1.sp),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    if (label.isEmpty()) {
        // The empty slot where the decimal point sits for other currencies.
        Box(modifier = modifier)
        return
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Vitt.radius.key))
            .background(Vitt.colors.surface)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = when (label) {
                    "." -> "Decimal point"
                    "⌫" -> "Delete last digit"
                    else -> "Type $label"
                },
                // Named, so an assistive-tech actions menu offers "Clear amount"
                // rather than asking for a physical hold.
                onLongClickLabel = if (onLongClick != null) "Clear amount" else null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            // Replaces the child's text rather than merging with it: merged,
            // the AX tree read "Decimal point, ." and "1, 1", and VoiceOver
            // spoke each key twice. Found with tools/axtree.py.
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = when (label) {
                    "." -> "Decimal point"
                    "⌫" -> "Delete"
                    else -> label
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 26.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = Vitt.colors.ink,
        )
    }
}
