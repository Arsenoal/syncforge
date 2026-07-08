package dev.syncforge.persistence

import app.cash.sqldelight.db.SqlDriver

/** Browser schema bootstrap runs via [createWebSyncForgePersistence] (suspend). */
internal actual fun bootstrapSyncForgePersistenceSchema(driver: SqlDriver) = Unit