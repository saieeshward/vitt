package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        item {
            Text("Ledgers", style = Vitt.type.title, color = Vitt.colors.ink)
        }

        // Pip leads the home screen rather than hiding in a tab: the character
        // is how the no-conversion rule is explained without words — one coin
        // slot per currency, and coins never move between them.
        item {
            ie.shoonya.vitt.ui.Pip(
                daysRecorded = daysRecorded,
                currencyCount = ledgers.size.coerceAtLeast(1),
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
        }

        if (ledgers.isEmpty()) {
            item { EmptyLedgers() }
        } else {
            items(ledgers) { LedgerCard(it) }
            item { NoTotalNote() }
        }

        if (owed.isNotEmpty()) {
            item { OwedRow(owed) }
        }
    }
}

@Composable
private fun LedgerCard(ledger: Ledger) {
    val colors = Vitt.colors
    val remaining = ledger.remaining()
    val pressure = ledger.pressure()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
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

        // Room remaining, not overspend — actionable rather than a verdict.
        Text(
            text = (remaining ?: ledger.net).display(),
            style = Vitt.type.moneyHero,
            color = colors.ink,
            textAlign = TextAlign.Start,
        )
        Text(
            text = when {
                remaining != null && remaining.minor >= 0 ->
                    "left of ${ledger.budget!!.display()}"
                remaining != null ->
                    "${remaining.abs().display()} past ${ledger.budget!!.display()}"
                else -> "in and out this month"
            },
            style = Vitt.type.label,
            color = colors.inkMuted,
        )

        pressure?.let { HealthBar(it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.loose),
        ) {
            Figure("In", ledger.received)
            Figure("Out", ledger.spent)
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
    Column {
        Text(label, style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        Text(amount.display(), style = Vitt.type.money, color = Vitt.colors.ink)
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
    // Surfaced passively here, never as a push notification.
    Text(
        "You're owed " + owed.values.joinToString(" and ") { it.display() },
        style = Vitt.type.body,
        color = Vitt.colors.accent,
        modifier = Modifier.padding(top = Vitt.space.tight),
    )
}

@Composable
private fun EmptyLedgers() {
    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
        Text("Nothing recorded yet.", style = Vitt.type.body, color = Vitt.colors.ink)
        Text(
            "Tap the middle button to log something. Every currency gets its own " +
                "ledger, and they are never added together.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}
