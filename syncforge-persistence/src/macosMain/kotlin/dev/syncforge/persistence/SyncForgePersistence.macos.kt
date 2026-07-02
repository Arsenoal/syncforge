package dev.syncforge.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.syncforge.api.ExperimentalSyncForgeApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@ExperimentalSyncForgeApi
@OptIn(ExperimentalForeignApi::class)
actual fun createDefaultSyncForgePersistence(databaseName: String): SyncForgePersistence {
    val supportDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Unable to resolve macOS Application Support directory for SyncForge persistence")

    val dbPath = supportDirectory.path + "/$databaseName"
    val driver = NativeSqliteDriver(
        schema = SyncForgePersistenceDatabase.Schema,
        name = dbPath,
    )
    return SyncForgePersistence.create(driver)
}