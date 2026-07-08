package dev.syncforge.persistence

import app.cash.sqldelight.async.coroutines.awaitCreate
import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Creates browser [SyncForgePersistence] with SQLDelight schema ready before first query.
 *
 * SQL.js storage is **in-memory** in the web-worker for 1.6.x (survives SPA navigation within
 * the same tab session; cleared on full page reload). IndexedDB-backed persistence is deferred.
 *
 * @param databaseName Reserved for per-app storage namespacing (cursor key); worker DB is
 * in-memory until IndexedDB lands.
 */
@ExperimentalSyncForgeApi
suspend fun createWebSyncForgePersistence(databaseName: String = "syncforge.db"): SyncForgePersistence {
    @Suppress("UNUSED_PARAMETER")
    val ignored = databaseName
    val driver = createWebSqlDelightDriver()
    SyncForgePersistenceDatabase.Schema.awaitCreate(driver)
    return SyncForgePersistence.create(driver)
}