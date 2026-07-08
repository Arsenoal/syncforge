package dev.syncforge.persistence

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.runBlocking

internal actual fun bootstrapSyncForgePersistenceSchema(driver: SqlDriver) {
    runBlocking {
        SyncForgePersistenceDatabase.Schema.awaitCreate(driver)
    }
}