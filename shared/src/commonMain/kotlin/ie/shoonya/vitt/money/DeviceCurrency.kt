package ie.shoonya.vitt.money

/**
 * The currency the device's region suggests, if the app knows it.
 *
 * Only ever a *starting* guess, on the first-run setup screen. Null when the
 * region names a currency the app does not carry, which is the common case: the
 * app knows six currencies and the world has rather more.
 *
 * Deliberately not used anywhere a figure is computed. Money is entered in a
 * currency the user picked, and inferring one from a region would be the app
 * deciding what a number means.
 */
expect fun deviceCurrency(): Currency?
