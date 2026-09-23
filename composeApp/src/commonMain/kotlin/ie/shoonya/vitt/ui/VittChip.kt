package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The one chip in the app. Everything that offers a choice uses it.
 *
 * Material's [FilterChip] is 32dp tall, which is eleven points under Apple's
 * minimum touch target and eight under Android's. That is not a rounding error:
 * driving the app through a real pointer, a tap aimed at the centre of the
 * *drawn* chip missed the account picker, no account was selected, and the Save
 * button stayed lit — so a transaction was one tap away from being filed against
 * nothing. Height is the whole fix, and it has to live in one place because
 * there are three dozen chips and any one of them missed is a chip that still
 * eats taps.
 *
 * The semantics are the other half. A filter chip's selected state is what the
 * chip is *for*, and without it a screen-reader user hears "AIB, button" for the
 * account they picked and "Revolut, button" for the one they did not. Stated
 * explicitly rather than trusted to Material, because the iOS accessibility
 * bridge published neither a selected trait nor a value for these chips: on a
 * VoiceOver walk of the Add sheet, nothing said which account, category or
 * currency was chosen.
 *
 * [Role.Button] rather than Checkbox or RadioButton: a chip here is sometimes a
 * single choice and sometimes several, and a role that promises which one it is
 * would be wrong half the time. The selected flag carries the state either way.
 */
@Composable
fun VittChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    val isSelected = selected
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        enabled = enabled,
        label = label,
        modifier = modifier
            .defaultMinSize(minHeight = TOUCH_TARGET)
            .semantics {
                this.selected = isSelected
                role = Role.Button
            },
    )
}

/** Apple's minimum, which is also the larger of the two platforms'. */
private val TOUCH_TARGET = 44.dp
