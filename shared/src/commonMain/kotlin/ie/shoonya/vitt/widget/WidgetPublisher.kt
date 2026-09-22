package ie.shoonya.vitt.widget

/**
 * Hands the snapshot to wherever the platform's widget can read it.
 *
 * Null clears it, which is what an emptied ledger should do: a widget showing a
 * stale figure is worse than one asking you to open the app, because a stale
 * figure looks like a current one.
 */
expect fun publishWidgetSnapshot(payload: String?)
