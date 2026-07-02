package dev.syncforge.persistence

import app.cash.sqldelight.db.SqlDriver
import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Multiplatform persistence entry point for SyncForge internal storage (outbox + conflicts).
 *
 * Platform drivers are created via [createDefaultSyncForgePersistence], [createSyncForgePersistence],
 * or [SyncForgePersistenceFactory.create].
 *
 * Repository wiring lives in `:syncforge` — use [outboxRepository] and [conflictStore] extensions.
 */
class SyncForgePersistence internal constructor(
    val database: SyncForgePersistenceDatabase,
    private val driver: SqlDriver,
) {

    companion object {
        fun create(driver: SqlDriver): SyncForgePersistence {
            val database = SyncForgePersistenceDatabase(driver)
            return SyncForgePersistence(database, driver)
        }
    }

    /**
     * Keeps AUTOINCREMENT counters aligned after a bulk import (Room → SQLDelight migration).
     */
    @ExperimentalSyncForgeApi
    fun reseedAutoincrementSequences(maxOutboxId: Long?, maxConflictId: Long?) {
        maxOutboxId?.let { reseedSequence(table = "syncforge_outbox", seq = it) }
        maxConflictId?.let { reseedSequence(table = "syncforge_conflicts", seq = it) }
    }

    private fun reseedSequence(table: String, seq: Long) {
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES (?, ?)",
            parameters = 2,
        ) {
            bindString(0, table)
            bindLong(1, seq)
        }
    }
}

/**
 * Creates a file-backed [SyncForgePersistence] with the platform default driver.
 *
 * - **iOS:** SQLite in the app documents directory
 * - **JVM:** file-backed SQLite (or in-memory for tests via [create])
 * - **Android:** requires [createSyncForgePersistence] with [android.content.Context] instead
 */
@ExperimentalSyncForgeApi
expect fun createDefaultSyncForgePersistence(databaseName: String = "syncforge.db"): SyncForgePersistence