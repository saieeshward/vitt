package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Currency

/**
 * Spike 2's harness. Not the product: this exists only to put the keypad on a
 * real device so its feel on iOS can be judged by a person, which is the one
 * question no test can answer.
 */
@Composable
fun SpikeApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var entry by remember { mutableStateOf(AmountEntry()) }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Spike 2: amount entry", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Currency.EUR, Currency.INR, Currency.JPY).forEach { c ->
                        FilterChip(
                            selected = entry.currency == c,
                            onClick = { entry = entry.withCurrency(c) },
                            label = { Text(c.code) },
                        )
                    }
                }

                AmountKeypad(entry = entry, onEntryChange = { entry = it })

                Text(
                    "minor units: ${entry.money.minor}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
