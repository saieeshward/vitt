package ie.shoonya.vitt.money

import java.util.Locale

actual fun deviceCurrency(): Currency? = runCatching {
    java.util.Currency.getInstance(Locale.getDefault()).currencyCode
}.getOrNull()?.let(Currency::ofCode)
