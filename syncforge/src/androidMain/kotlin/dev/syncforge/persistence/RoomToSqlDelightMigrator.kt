package dev.syncforge.persistence

import android.content.Context
import dev.syncforge.outbox.SyncForgeDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * One-time migration from Room internal storage (`syncforge_outbox.db`) to SQLDelight (`syncforge.db`).
 *
 * Invoked automatically when [dev.syncforge.SyncForge.android] uses SQLDelight (default since 0.6.0).
 */
object RoomToSqlDelightMigrator {

    private const val PREFS_NAME = "syncforge_migration"
    private const val KEY_ROOM_MIGRATED = "room_to_sqldelight_v1"
    const val ROOM_DATABASE_NAME: String = "syncforge_outbox.db"

    data class Result(
        val performed: Boolean,
        val outboxRows: Int,
        val conflictRows: Int,
        val roomDatabaseDeleted: Boolean,
    )

    /**
     * Copies pending outbox rows and conflict audit rows from Room into SQLDelight when:
     * - migration has not run before, and
     * - the Room database file exists, and
     * - SQLDelight tables are empty (safe re-run guard).
     */
    fun migrateIfNeeded(
        context: Context,
        persistence: SyncForgePersistence,
        deleteRoomDatabaseAfterMigration: Boolean = true,
    ): Result = runBlocking {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ROOM_MIGRATED, false)) {
            return@runBlocking Result(performed = false, outboxRows = 0, conflictRows = 0, roomDatabaseDeleted = false)
        }

        val roomExists = context.databaseList().any { it == ROOM_DATABASE_NAME }
        if (!roomExists) {
            prefs.edit().putBoolean(KEY_ROOM_MIGRATED, true).apply()
            return@runBlocking Result(performed = false, outboxRows = 0, conflictRows = 0, roomDatabaseDeleted = false)
        }

        val outboxQueries = persistence.database.outboxQueries
        val conflictQueries = persistence.database.conflictsQueries

        val existingOutbox = outboxQueries.observeAll().executeAsList()
        val existingConflicts = conflictQueries.observeAll().executeAsList()
        if (existingOutbox.isNotEmpty() || existingConflicts.isNotEmpty()) {
            prefs.edit().putBoolean(KEY_ROOM_MIGRATED, true).apply()
            return@runBlocking Result(performed = false, outboxRows = 0, conflictRows = 0, roomDatabaseDeleted = false)
        }

        val roomDb = SyncForgeDatabaseFactory.create(context, ROOM_DATABASE_NAME)
        val roomOutbox = roomDb.outboxDao().observeAll().first()
        val roomConflicts = roomDb.conflictDao().observeAll().first()

        persistence.database.transaction {
            roomOutbox.forEach { entry ->
                outboxQueries.insertOutboxEntryWithId(
                    id = entry.id,
                    entityType = entry.entityType,
                    entityId = entry.entityId,
                    changeType = entry.changeType,
                    payloadJson = entry.payloadJson,
                    rollbackSnapshotJson = entry.rollbackSnapshotJson,
                    localVersion = entry.localVersion,
                    createdAtMillis = entry.createdAtMillis,
                    retryCount = entry.retryCount.toLong(),
                    lastError = entry.lastError,
                    nextRetryAtMillis = entry.nextRetryAtMillis,
                )
            }
            roomConflicts.forEach { entry ->
                conflictQueries.insertConflictWithId(
                    id = entry.id,
                    entityType = entry.entityType,
                    entityId = entry.entityId,
                    localJson = entry.localJson,
                    remoteJson = entry.remoteJson,
                    localUpdatedAtMillis = entry.localUpdatedAtMillis,
                    remoteServerVersion = entry.remoteServerVersion,
                    remoteUpdatedAtMillis = entry.remoteUpdatedAtMillis,
                    detectedAtMillis = entry.detectedAtMillis,
                    status = entry.status,
                    resolutionKind = entry.resolutionKind,
                )
            }
        }

        persistence.reseedAutoincrementSequences(
            maxOutboxId = roomOutbox.maxOfOrNull { it.id },
            maxConflictId = roomConflicts.maxOfOrNull { it.id },
        )

        val roomDeleted = if (deleteRoomDatabaseAfterMigration) {
            context.deleteDatabase(ROOM_DATABASE_NAME)
        } else {
            false
        }

        prefs.edit().putBoolean(KEY_ROOM_MIGRATED, true).apply()
        Result(
            performed = true,
            outboxRows = roomOutbox.size,
            conflictRows = roomConflicts.size,
            roomDatabaseDeleted = roomDeleted,
        )
    }

}