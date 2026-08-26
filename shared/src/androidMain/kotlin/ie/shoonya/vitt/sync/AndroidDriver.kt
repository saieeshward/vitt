package ie.shoonya.vitt.sync

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ie.shoonya.vitt.db.VittDatabase

/** The on-device database. Android File-Based Encryption covers it at rest. */
fun androidDriver(context: Context, name: String = "vitt.db"): SqlDriver =
    AndroidSqliteDriver(VittDatabase.Schema, context, name)
