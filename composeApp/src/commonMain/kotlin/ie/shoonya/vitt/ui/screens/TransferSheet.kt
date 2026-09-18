package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import ie.shoonya.vitt.model.Account
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.Rate
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Moving money between two of the user's own accounts.
 *
 * Two legs, entered one at a time. Across currencies the second leg is what
 * *arrived*, not a conversion of the first — which is the whole point: the rate
 * is then a fact derived from two observed amounts, and cannot move afterwards
 * the way a market rate does.
 *
 * One keypad is on screen at a time, so the two legs are separate steps.
 */
@Composable
fun TransferSheet(
    accounts: List<Account>,
    onTransfer: (from: Account, to: Account, sent: Money, received: Money) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var from by remember { mutableStateOf(accounts.firstOrNull()) }
    var to by remember { mutableStateOf(accounts.drop(1).firstOrNull()) }
    // Seeded from the accounts that start selected, not left at the default
    // currency. Only the picker's onSelect re-synced these, so a destination the
    // user never tapped kept EUR and the keypad showed "€0.02" while money was
    // arriving in rupees. The rate was right and the display lied, which is worse
    // than both being wrong.
    var sent by remember {
        mutableStateOf(AmountEntry(currency = accounts.firstOrNull()?.currency ?: Currency.EUR))
    }
    var received by remember {
        mutableStateOf(
            AmountEntry(currency = accounts.drop(1).firstOrNull()?.currency ?: Currency.EUR),
        )
    }
    var onSecondLeg by remember { mutableStateOf(false) }

    val source = from
    val destination = to
    val crossCurrency = source != null && destination != null &&
        source.currency != destination.currency

    // Same currency needs one amount, so the second leg mirrors the first unless
    // a fee was taken — and a fee is entered by editing the received leg.
    // Re-read in the account's currency at the point of use. The entry's minor
    // value depends on its currency's exponent, so an entry left in yen while
    // the destination moved to euro would have saved ¥100 as €1.00.
    val sentMoney = source?.let { sent.withCurrency(it.currency).money }
    val receivedMoney = destination?.let {
        val leg = received.withCurrency(it.currency)
        if (crossCurrency) leg.money
        else if (leg.isEmpty) sent.withCurrency(it.currency).money else leg.money
    }

    val ready = source != null && destination != null && source != destination &&
        (sentMoney?.minor ?: 0L) > 0L && (receivedMoney?.minor ?: 0L) > 0L

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Two pickers plus a keypad plus the rate note is taller than the
            // sheet, and a plain Column would simply clip the overflow — putting
            // the rate note out of reach on shorter devices. Scrolling keeps all
            // of it available.
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (onSecondLeg) ({ onSecondLeg = false }) else onCancel) {
                Text(if (onSecondLeg) "Back" else "Cancel")
            }
            Text("Move money", style = Vitt.type.title, color = Vitt.colors.ink)
            if (crossCurrency && !onSecondLeg) {
                Button(
                    enabled = (sentMoney?.minor ?: 0L) > 0L,
                    onClick = { onSecondLeg = true },
                ) { Text("Next") }
            } else {
                Button(
                    enabled = ready,
                    onClick = { onTransfer(source!!, destination!!, sentMoney!!, receivedMoney!!) },
                ) { Text("Save") }
            }
        }

        if (accounts.size < 2) {
            Text(
                "Two accounts are needed before money can move.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            return@Column
        }

        AccountPicker(
            label = "From",
            accounts = accounts,
            selected = source,
            onSelect = {
                from = it
                sent = sent.withCurrency(it.currency)
                if (to == it) {
                    val next = accounts.firstOrNull { a -> a != it }
                    to = next
                    // The destination moved, so its keypad must move with it.
                    next?.let { n -> received = received.withCurrency(n.currency) }
                }
            },
        )
        AccountPicker(
            label = "To",
            // Never offer the source as the destination; a transfer to itself is
            // not a thing that can happen.
            accounts = accounts.filter { it != source },
            selected = destination,
            onSelect = {
                to = it
                received = received.withCurrency(it.currency)
            },
        )

        if (onSecondLeg) {
            Text(
                "How much arrived in ${destination?.name}",
                style = Vitt.type.caption,
                color = Vitt.colors.inkMuted,
            )
            Text(
                "What actually landed, not a conversion.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            AmountKeypad(entry = received, onEntryChange = { received = it })
        } else {
            Text(
                if (crossCurrency) "How much left ${source.name}" else "How much to move",
                style = Vitt.type.caption,
                color = Vitt.colors.inkMuted,
            )
            AmountKeypad(entry = sent, onEntryChange = { sent = it })
        }

        // The derived rate, live. Shown as soon as both legs exist, because
        // seeing it before saving is what makes it feel like a recorded fact
        // rather than a number the app made up later.
        if (crossCurrency && sentMoney != null && receivedMoney != null &&
            sentMoney.minor > 0 && receivedMoney.minor > 0
        ) {
            RateNote(
                Rate(
                    from = sentMoney.currency,
                    to = receivedMoney.currency,
                    sentMinor = sentMoney.minor,
                    receivedMinor = receivedMoney.minor,
                ),
            )
        } else if (!crossCurrency && sentMoney != null && receivedMoney != null &&
            receivedMoney.minor in 1 until sentMoney.minor
        ) {
            Text(
                "${(sentMoney - receivedMoney).displayUnsigned()} in fees",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun RateNote(rate: Rate) {
    val shown = rate.toPlainString(4) ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Vitt.radius.pill))
            .background(Vitt.colors.accent.copy(alpha = 0.08f))
            .padding(Vitt.space.base),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        Text(
            "$shown ${rate.to.code} per ${rate.from.code}",
            style = Vitt.type.money,
            color = Vitt.colors.ink,
        )
        Text(
            "Locked to this transfer. It will not change when the market does.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccountPicker(
    label: String,
    accounts: List<Account>,
    selected: Account?,
    onSelect: (Account) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
        Text(label, style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        ) {
            accounts.forEach { account ->
                FilterChip(
                    selected = selected == account,
                    onClick = { onSelect(account) },
                    label = {
                        Text("${account.name} · ${account.currency.code}", style = Vitt.type.label)
                    },
                )
            }
        }
    }
}
