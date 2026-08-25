package ie.shoonya.vitt.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import ie.shoonya.vitt.db.VittDatabase

actual fun testDriver(): SqlDriver = inMemoryDriver(VittDatabase.Schema)
