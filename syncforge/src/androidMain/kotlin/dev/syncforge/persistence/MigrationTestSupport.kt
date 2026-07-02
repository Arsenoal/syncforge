package dev.syncforge.persistence

import android.content.Context
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictEntryEntity
import dev.syncforge.conflict.ConflictStatus
import dev.syncforge.outbox.OutboxEntryEntity
import dev.syncforge.outbox.SyncForgeDatabaseFactory
import kotlinx.coroutines.flow.first

/**
 * Helpers for instrumented and unit tests that simulate a pre-0.6.0 Room upgrade.
 *
 * Not required for production apps — migration runs automatically via [SyncForge.android].
 */
@ExperimentalSyncForgeApi
object MigrationTestSupport {

    /**
     * Isolated SQLDelight file for instrumented migration tests in the [:sample] process.
     * Must not use [DEFAULT_DATABASE_NAME] — [SampleApplication] keeps `syncforge.db` open.
     */
    const val INSTRUMENTED_TEST_DATABASE_NAME: String = "syncforge_migration_instrumented_test.db"

    fun resetMigrationState(
        context: Context,
        sqlDelightDatabaseName: String = DEFAULT_DATABASE_NAME,
    ) {
        context.applicationContext
            .getSharedPreferences("syncforge_migration", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.applicationContext.deleteDatabase(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME)
        context.applicationContext.deleteDatabase(sqlDelightDatabaseName)
        RoomToSqlDelightMigrator.failAfterOutboxRows = null
    }

    suspend fun seedLegacyRoomStorage(
        context: Context,
        outboxCount: Int = 1,
        conflictCount: Int = 0,
        entityType: String = "tasks",
    ) {
        val roomDb = SyncForgeDatabaseFactory.create(
            context.applicationContext,
            RoomToSqlDelightMigrator.ROOM_DATABASE_NAME,
        )
        repeat(outboxCount) { index ->
            roomDb.outboxDao().insert(
                OutboxEntryEntity(
                    entityType = entityType,
                    entityId = "entity-$index",
                    changeType = "UPDATE",
                    payloadJson = """{"id":"entity-$index","title":"Pending $index"}""",
                    rollbackSnapshotJson = null,
                    localVersion = index.toLong() + 1,
                    createdAtMillis = 100L + index,
                    retryCount = index % 3,
                    lastError = if (index % 5 == 0) "network" else null,
                    nextRetryAtMillis = if (index % 5 == 0) 200L + index else null,
                ),
            )
        }
        repeat(conflictCount) { index ->
            roomDb.conflictDao().insert(
                ConflictEntryEntity(
                    entityType = entityType,
                    entityId = "conflict-$index",
                    localJson = """{"id":"conflict-$index"}""",
                    remoteJson = """{"id":"conflict-$index","title":"Remote"}""",
                    localUpdatedAtMillis = 50L + index,
                    remoteServerVersion = 3L + index,
                    remoteUpdatedAtMillis = 60L + index,
                    detectedAtMillis = 70L + index,
                    status = ConflictStatus.OPEN.name,
                    resolutionKind = null,
                ),
            )
        }
        roomDb.outboxDao().observeAll().first()
    }
}