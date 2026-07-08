package dev.syncforge.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.syncforge.api.ExperimentalSyncForgeApi
import java.io.File

@ExperimentalSyncForgeApi
actual fun createDefaultSyncForgePersistence(databaseName: String): SyncForgePersistence {
    val dbFile = File(System.getProperty("java.io.tmpdir"), databaseName)
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    bootstrapSyncForgePersistenceSchema(driver)
    return SyncForgePersistence.create(driver)
}