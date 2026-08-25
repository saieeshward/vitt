package ie.shoonya.vitt.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import ie.shoonya.vitt.db.VittDatabase

/** The on-device database. iOS Data Protection covers it at rest. */
fun iosDriver(name: String = "vitt.db"): SqlDriver =
    NativeSqliteDriver(VittDatabase.Schema, name)
