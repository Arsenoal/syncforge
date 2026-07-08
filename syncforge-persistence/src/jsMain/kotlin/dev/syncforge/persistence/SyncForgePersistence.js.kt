package dev.syncforge.persistence

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Browser persistence via SQLDelight web-worker driver (SQL.js in a dedicated worker).
 *
 * Prefer [createWebSyncForgePersistence] or `SyncForge.web { }` on Kotlin/JS —
 * they run async schema bootstrap before the first query.
 */
@ExperimentalSyncForgeApi
actual fun createDefaultSyncForgePersistence(databaseName: String): SyncForgePersistence {
    @Suppress("UNUSED_PARAMETER")
    val ignored = databaseName
    return SyncForgePersistence.create(createWebSqlDelightDriver())
}