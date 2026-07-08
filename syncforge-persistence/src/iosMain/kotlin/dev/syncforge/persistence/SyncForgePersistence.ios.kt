package dev.syncforge.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.syncforge.api.ExperimentalSyncForgeApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@ExperimentalSyncForgeApi
@OptIn(ExperimentalForeignApi::class)
actual fun createDefaultSyncForgePersistence(databaseName: String): SyncForgePersistence {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Unable to resolve iOS documents directory for SyncForge persistence")

    val dbPath = documentDirectory.path + "/$databaseName"
    val driver = NativeSqliteDriver(
        schema = SyncForgePersistenceNoOpSyncSchema,
        name = dbPath,
    )
    bootstrapSyncForgePersistenceSchema(driver)
    return SyncForgePersistence.create(driver)
}