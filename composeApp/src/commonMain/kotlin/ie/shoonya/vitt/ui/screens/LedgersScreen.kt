package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.shoonya.vitt.model.Ledger
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * The home screen: one card per currency, each complete on its own.
 *
 * There is no total anywhere, and the line under the last card says so. The
 * absence is stated rather than merely left out, so it reads as rigour instead
 * of a missing feature.
 */
@Composable
fun LedgersScreen(
    ledgers: List<Ledger>,
    owed: Map<Currency, Money>,
    daysRecorded: Int,
    onSetBudget: (Currency) -> Unit,
    balances: List<ie.shoonya.vitt.model.AccountBalance>,
    transfers: List<ie.shoonya.vitt.model.Transfer>,
    onAddAccount: () -> Unit,
    onTransfer: () -> Unit,
    accountName: (String) -> String,
    formatDay: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        item {
            Text("Ledgers", style = Vitt.type.display, color = Vitt.colors.ink)
        }

        // Pip leads the home screen rather than hiding in a tab: the character
        // is how the no-conversion rule is explained without words — one coin
        // slot per currency, and coins never move between them.
        item {
            ie.shoonya.vitt.ui.Pip(
                daysRecorded = daysRecorded,
                currencyCount = ledgers.size,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
        }

        if (ledgers.isEmpty()) {
            item { EmptyLedgers() }
        } else {
            items(ledgers) { LedgerCard(it, onSetBudget) }
            item { NoTotalNote() }
        }

        if (owed.isNotEmpty()) {
            item { OwedRow(owed) }
        }

        accountsSection(
            balances = balances,
            transfers = transfers,
            onAddAccount = onAddAccount,
            onTransfer = onTransfer,
            accountName = accountName,
            formatDay = formatDay,
        )
    }
}

@Composable
private fun LedgerCard(ledger: Ledger, onSetBudget: (Currency) -> Unit) {
    val colors = Vitt.colors
    val remaining = ledger.remaining()
    val pressure = ledger.pressure()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // A white card lifted off the cream ground, per the design's own
            // `box-shadow: 0 2px 10px rgba(36,31,51,0.06)`. A card tinted a
            // shade of the background instead reads as muddy, which is what an
            // earlier pass here produced.
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(Vitt.radius.card),
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(RoundedCornerShape(Vitt.radius.card))
            .background(colors.card)
            // The whole card opens the budget for its currency. Tapping the thing
            // the limit applies to needs no separate affordance, and the card has
            // no other action competing for the gesture.
            .clickable { onSetBudget(ledger.currency) }
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Chroma lives in the dot, never in a filled card.
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(colors.currency(ledger.index)),
            )
            Text(
                "  ${ledger.currency.code}",
                style = Vitt.type.caption,
                color = colors.inkMuted,
            )
        }

        // Room remaining when a budget exists; otherwise what was spent.
        //
        // Never a negative hero figure. The one loud number on a screen must not
        // be bad news with a minus sign in front of it — that is the shape the
        // tone rules exist to prevent. Spending is stated as a positive amount
        // out, which is the same fact without the verdict.
        Text(
            text = (remaining?.abs() ?: ledger.spent).displayUnsigned(),
            style = Vitt.type.moneyHero,
            color = colors.ink,
            textAlign = TextAlign.Start,
        )
        Text(
            text = when {
                remaining != null && remaining.minor >= 0 ->
                    // A limit is a threshold, not a flow, so it takes no sign. `display()`
                    // prefixes "+" for anything positive, which rendered the budget
                    // as "left of +€1,800.00".
                    "left of ${ledger.budget!!.displayUnsigned()}"
                remaining != null ->
                    "past ${ledger.budget!!.displayUnsigned()}"
                else -> "out this month · tap to set a budget"
            },
            style = Vitt.type.label,
            color = colors.inkMuted,
        )

        pressure?.let { HealthBar(it) }

        // Only what the hero figure does not already say. Repeating the same
        // number twice on one card wastes the "one loud number" rule it is
        // there to serve.
        val hasSecondary = ledger.received.minor != 0L || remaining != null
        if (hasSecondary) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Vitt.space.snug),
                horizontalArrangement = Arrangement.spacedBy(Vitt.space.section),
            ) {
                if (ledger.received.minor != 0L) Figure("In", ledger.received)
                if (remaining != null) Figure("Out", ledger.spent)
            }
        }
    }
}

/**
 * Budget health as accent fading to neutral.
 *
 * Never green to red: a red ledger is a verdict, and over budget is the absence
 * of accent rather than an alarm — the card goes quiet instead of shouting.
 */
@Composable
private fun HealthBar(pressure: Float) {
    val colors = Vitt.colors
    Box(
        Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
            .background(colors.inkFaint.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(pressure.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.health(pressure)),
        )
    }
}

@Composable
private fun Figure(label: String, amount: Money) {
    // Inline rather than stacked. A single stacked stat under a hero figure
    // reads as an orphaned fragment; on one line it reads as a footnote, which
    // is what it is.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Vitt.type.caption, color = Vitt.colors.inkFaint)
        Text(
            // The label already says which way the money went, and `spent` is
            // held as a positive magnitude — so signing it produced the flatly
            // contradictory "Out +€1,852.04".
            "  " + amount.displayUnsigned(),
            style = Vitt.type.money.copy(fontSize = 14.sp),
            color = Vitt.colors.inkMuted,
        )
    }
}

@Composable
private fun NoTotalNote() {
    Text(
        "Ledgers are kept separate on purpose. VITT never converts, so no figure " +
            "here moves because a rate moved.",
        style = Vitt.type.label,
        color = Vitt.colors.inkMuted,
        modifier = Modifier.padding(top = Vitt.space.tight),
    )
}

@Composable
private fun OwedRow(owed: Map<Currency, Money>) {
    // Surfaced passively here, never as a push notification — and on a card, so
    // it reads as a standing fact rather than a stray link.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Vitt.radius.card))
            .background(Vitt.colors.accent.copy(alpha = 0.08f))
            .padding(Vitt.space.loose),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "You're owed",
            style = Vitt.type.body,
            color = Vitt.colors.inkMuted,
        )
        Text(
            "  " + owed.values.joinToString("  ·  ") { it.display() },
            style = Vitt.type.money,
            color = Vitt.colors.ink,
        )
    }
}

@Composable
private fun EmptyLedgers() {
    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
        Text("Nothing recorded yet.", style = Vitt.type.title, color = Vitt.colors.ink)
        Text(
            "Tap the middle button to log something. Every currency gets its own " +
                "ledger, and they are never added together.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}
