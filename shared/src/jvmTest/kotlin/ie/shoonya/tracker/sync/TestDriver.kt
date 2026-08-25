package ie.shoonya.tracker.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ie.shoonya.tracker.db.VittDatabase

/**
 * A real SQLite database in memory. Not a stub: the same driver, schema and SQL
 * the app runs, so constraint violations and transaction semantics behave as
 * they will on a device.
 */
actual fun testDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { VittDatabase.Schema.create(it) }
