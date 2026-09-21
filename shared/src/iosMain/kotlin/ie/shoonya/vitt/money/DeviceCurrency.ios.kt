package ie.shoonya.vitt.money

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.currencyCode

actual fun deviceCurrency(): Currency? =
    NSLocale.currentLocale.currencyCode?.let(Currency::ofCode)
