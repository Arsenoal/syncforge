package dev.syncforge.persistence

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import dev.syncforge.conflict.ConflictEntryEntity
import dev.syncforge.outbox.OutboxEntryEntity
import dev.syncforge.outbox.SyncForgeDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * One-time migration from Room internal storage (`syncforge_outbox.db`) to SQLDelight (`syncforge.db`).
 *
 * Invoked automatically when [dev.syncforge.SyncForge.android] uses SQLDelight (default since 0.6.0).
 */
object RoomToSqlDelightMigrator {

    private const val TAG = "SyncForgeMigrator"
    private const val PREFS_NAME = "syncforge_migration"
    private const val KEY_ROOM_MIGRATED = "room_to_sqldelight_v1"
    private const val BATCH_SIZE = 100

    const val ROOM_DATABASE_NAME: String = "syncforge_outbox.db"

    enum class Status {
        /** Migration completed and Room data removed (or nothing to migrate). */
        SUCCESS,
        /** [KEY_ROOM_MIGRATED] already set — no work performed. */
        SKIPPED_ALREADY_DONE,
        /** No Room database on disk — marked complete without copying rows. */
        SKIPPED_NO_ROOM,
        /** Migration failed; [KEY_ROOM_MIGRATED] is not set so the next launch retries. */
        FAILED,
    }

    data class Result(
        val status: Status,
        val outboxRows: Int = 0,
        val conflictRows: Int = 0,
        val roomDatabaseDeleted: Boolean = false,
        val errorMessage: String? = null,
    ) {
        /** True when rows were copied successfully this run. */
        val performed: Boolean get() = status == Status.SUCCESS && (outboxRows > 0 || conflictRows > 0)

        val succeeded: Boolean get() = status == Status.SUCCESS
    }

    /**
     * Test hook: throw after inserting this many outbox rows (first attempt only).
     * Cleared automatically when the simulated failure fires.
     */
    @VisibleForTesting
    @JvmField
    var failAfterOutboxRows: Int? = null

    /**
     * Copies pending outbox rows and conflict audit rows from Room into SQLDelight when:
     * - migration has not run before, and
     * - the Room database file exists.
     *
     * Inserts use `INSERT OR REPLACE` in batches so a failed run can be retried safely.
     */
    fun migrateIfNeeded(
        context: Context,
        persistence: SyncForgePersistence,
        deleteRoomDatabaseAfterMigration: Boolean = true,
    ): Result = runBlocking {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ROOM_MIGRATED, false)) {
            log("skip: already marked migrated")
            return@runBlocking Result(status = Status.SKIPPED_ALREADY_DONE)
        }

        val roomExists = context.databaseList().any { it == ROOM_DATABASE_NAME }
        if (!roomExists) {
            markMigrated(prefs)
            log("skip: Room database not found")
            return@runBlocking Result(status = Status.SKIPPED_NO_ROOM)
        }

        val roomDb = SyncForgeDatabaseFactory.create(context, ROOM_DATABASE_NAME)
        val roomOutbox = roomDb.outboxDao().observeAll().first()
        val roomConflicts = roomDb.conflictDao().observeAll().first()

        if (roomOutbox.isEmpty() && roomConflicts.isEmpty()) {
            val roomDeleted = deleteRoomIfRequested(context, deleteRoomDatabaseAfterMigration)
            markMigrated(prefs)
            log("success: empty Room database removed=$roomDeleted")
            return@runBlocking Result(
                status = Status.SUCCESS,
                roomDatabaseDeleted = roomDeleted,
            )
        }

        try {
            log(
                "start: outbox=${roomOutbox.size} conflicts=${roomConflicts.size} " +
                    "batchSize=$BATCH_SIZE",
            )
            migrateOutboxBatched(persistence, roomOutbox)
            migrateConflictsBatched(persistence, roomConflicts)

            persistence.reseedAutoincrementSequences(
                maxOutboxId = roomOutbox.maxOfOrNull { it.id },
                maxConflictId = roomConflicts.maxOfOrNull { it.id },
            )

            val roomDeleted = deleteRoomIfRequested(context, deleteRoomDatabaseAfterMigration)
            markMigrated(prefs)
            log(
                "success: outbox=${roomOutbox.size} conflicts=${roomConflicts.size} " +
                    "roomDeleted=$roomDeleted",
            )
            Result(
                status = Status.SUCCESS,
                outboxRows = roomOutbox.size,
                conflictRows = roomConflicts.size,
                roomDatabaseDeleted = roomDeleted,
            )
        } catch (t: Throwable) {
            log("failed: ${t.message} — will retry on next launch", t)
            Result(
                status = Status.FAILED,
                outboxRows = persistence.database.outboxQueries.observeAll().executeAsList().size,
                conflictRows = persistence.database.conflictsQueries.observeAll().executeAsList().size,
                errorMessage = t.message,
            )
        }
    }

    private fun migrateOutboxBatched(
        persistence: SyncForgePersistence,
        roomOutbox: List<OutboxEntryEntity>,
    ) {
        var inserted = 0
        roomOutbox.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            persistence.database.transaction {
                batch.forEach { entry ->
                    maybeSimulateOutboxFailure(++inserted)
                    persistence.database.outboxQueries.insertOutboxEntryWithId(
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
            }
            log("outbox batch ${batchIndex + 1}: inserted ${batch.size} rows (total=$inserted)")
        }
    }

    private fun migrateConflictsBatched(
        persistence: SyncForgePersistence,
        roomConflicts: List<ConflictEntryEntity>,
    ) {
        roomConflicts.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            persistence.database.transaction {
                batch.forEach { entry ->
                    persistence.database.conflictsQueries.insertConflictWithId(
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
            log("conflicts batch ${batchIndex + 1}: inserted ${batch.size} rows")
        }
    }

    private fun maybeSimulateOutboxFailure(insertedCount: Int) {
        val threshold = failAfterOutboxRows ?: return
        if (insertedCount >= threshold) {
            failAfterOutboxRows = null
            error("Simulated migrator failure after $insertedCount outbox rows")
        }
    }

    private fun markMigrated(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean(KEY_ROOM_MIGRATED, true).apply()
    }

    private fun deleteRoomIfRequested(context: Context, deleteRoomDatabase: Boolean): Boolean =
        if (deleteRoomDatabase) {
            context.deleteDatabase(ROOM_DATABASE_NAME)
        } else {
            false
        }

    private fun log(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.i(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
    }
}