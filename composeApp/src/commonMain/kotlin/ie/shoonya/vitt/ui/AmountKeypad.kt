package ie.shoonya.vitt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫"),
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { label ->
                    KeypadKey(
                        label = label,
                        modifier = Modifier.weight(1f).fillMaxSize().padding(vertical = 4.dp),
                        onClick = {
                            onEntryChange(
                                when (label) {
                                    "C" -> entry.clear()
                                    "⌫" -> entry.backspace()
                                    else -> entry.press(label.first())
                                }
                            )
                        },
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
            // Screen readers should hear the amount, not the digit string.
            .semantics { contentDescription = "Amount ${entry.display()}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = entry.display(),
            fontSize = 48.sp,
            textAlign = TextAlign.Center,
            color = if (entry.isEmpty) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun KeypadKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = when (label) {
                    "C" -> "Clear"
                    "⌫" -> "Delete last digit"
                    else -> label
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
