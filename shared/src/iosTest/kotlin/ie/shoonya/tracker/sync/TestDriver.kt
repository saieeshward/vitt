package ie.shoonya.tracker.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import ie.shoonya.tracker.db.VittDatabase

actual fun testDriver(): SqlDriver = inMemoryDriver(VittDatabase.Schema)
