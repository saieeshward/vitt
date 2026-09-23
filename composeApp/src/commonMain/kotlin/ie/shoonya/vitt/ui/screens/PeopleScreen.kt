package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Who owes what.
 *
 * The unit is a person, and their debts stay in the currencies they were incurred
 * in. Two currencies means two figures, never one converted total — the same rule
 * as the ledgers, and for the same reason: a blended number would move with the
 * market after the fact.
 */
@Composable
fun PeopleScreen(
    participants: List<String>,
    outstandingFor: (String) -> Map<Currency, Money>,
    splitsFor: (String) -> List<Transaction>,
    onShare: (String) -> Unit,
    onOpenSplit: (Transaction) -> Unit,
    /** Opens the Add sheet with the split already on, for the empty state. */
    onSplitSomething: () -> Unit,
    formatDay: (Int) -> String,
    /** Room kept at the foot for the companion's band, as the other tabs keep. */
    companionInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Vitt.space.loose,
            top = Vitt.space.loose,
            end = Vitt.space.loose,
            bottom = Vitt.space.loose + companionInset,
        ),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        item { Text("People", style = Vitt.type.display, color = Vitt.colors.ink) }

        if (participants.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
                    Text("Nobody owes you anything.", style = Vitt.type.title, color = Vitt.colors.ink)
                    Text(
                        "Split an expense when you log it. Anything outstanding shows up here.",
                        style = Vitt.type.label,
                        color = Vitt.colors.inkMuted,
                    )
                    // A tab that only ever describes something you do elsewhere
                    // is a dead end, and this one is reached by people looking
                    // for exactly the thing it will not let them start. The
                    // sentence above stays — it is still where splits come from
                    // — and the button saves the trip.
                    TextButton(onClick = onSplitSomething, contentPadding = PaddingValues(0.dp)) {
                        Text("Split something now", style = Vitt.type.label)
                    }
                }
            }
        } else {
            items(participants) { person ->
                PersonCard(
                    person = person,
                    outstanding = outstandingFor(person),
                    splits = splitsFor(person),
                    onShare = { onShare(person) },
                    onOpenSplit = onOpenSplit,
                    formatDay = formatDay,
                )
            }
            item {
                Text(
                    "Kept in the currency each expense was in, never netted across them.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: String,
    outstanding: Map<Currency, Money>,
    splits: List<Transaction>,
    onShare: () -> Unit,
    onOpenSplit: (Transaction) -> Unit,
    formatDay: (Int) -> String,
) {
    val colors = Vitt.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(Vitt.radius.card),
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(RoundedCornerShape(Vitt.radius.card))
            .background(colors.card)
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
    ) {
        // The name leads and the address sits under it. An address is a poor
        // name — nobody thinks of the person they split a taxi with as
        // anya@example.com — but it is still the identity the summary goes to,
        // so it stays visible and checkable rather than being hidden.
        Text(
            ie.shoonya.vitt.model.PersonName.of(person),
            style = Vitt.type.title,
            color = colors.ink,
        )
        ie.shoonya.vitt.model.PersonName.address(person)
            ?.takeIf { it != ie.shoonya.vitt.model.PersonName.of(person) }
            ?.let {
                Text(it, style = Vitt.type.label, color = colors.inkFaint, maxLines = 1)
            }

        // One figure per currency, listed. Two lines is the honest shape.
        outstanding.forEach { (currency, amount) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("owes you", style = Vitt.type.label, color = colors.inkMuted)
                Text(amount.displayUnsigned(), style = Vitt.type.money, color = colors.ink)
            }
        }

        splits.forEach { split ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSplit(split) }
                    .padding(vertical = Vitt.space.hair),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    buildString {
                        append(formatDay(split.day))
                        append(" · ")
                        append(split.merchantLabel ?: split.categoryOrNull?.label ?: "expense")
                        // Says why the line is a fraction of what was owed.
                        if (split.splitWith.size > 1) append(" · 1 of ${split.splitWith.size}")
                    },
                    style = Vitt.type.label,
                    color = colors.inkMuted,
                )
                Text(
                    // This person's share, not the split's whole remainder. The
                    // card total sums these, so showing the remainder here would
                    // make the lines disagree with the figure above them.
                    split.outstandingFor(person)?.displayUnsigned() ?: "",
                    style = Vitt.type.label,
                    color = colors.inkFaint,
                )
            }
        }

        // Sending is the only channel that reliably reaches them: there is no
        // server here, so there is no push notification to send.
        TextButton(onClick = onShare, modifier = Modifier.padding(top = Vitt.space.hair)) {
            Text("Send a summary")
        }
    }
}
