package dev.syncforge.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.syncforge.conflict.ConflictEntryEntity
import dev.syncforge.conflict.ConflictStatus
import dev.syncforge.outbox.OutboxEntryEntity
import dev.syncforge.outbox.SyncForgeDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomToSqlDelightMigratorTest {

    private lateinit var context: Context
    private val sqlDelightDbName = "migrator_test_syncforge.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("syncforge_migration", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME)
        context.deleteDatabase(sqlDelightDbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME)
        context.deleteDatabase(sqlDelightDbName)
    }

    @Test
    fun migrateIfNeeded_copiesRoomOutboxAndConflicts() = runTest {
        val roomDb = SyncForgeDatabaseFactory.create(context, RoomToSqlDelightMigrator.ROOM_DATABASE_NAME)
        roomDb.outboxDao().insert(
            OutboxEntryEntity(
                entityType = "tasks",
                entityId = "task-1",
                changeType = "UPDATE",
                payloadJson = """{"id":"task-1","title":"Pending"}""",
                rollbackSnapshotJson = null,
                localVersion = 2,
                createdAtMillis = 100L,
                retryCount = 1,
                lastError = "network",
                nextRetryAtMillis = 200L,
            ),
        )
        roomDb.conflictDao().insert(
            ConflictEntryEntity(
                entityType = "tasks",
                entityId = "task-2",
                localJson = """{"id":"task-2"}""",
                remoteJson = """{"id":"task-2","title":"Remote"}""",
                localUpdatedAtMillis = 50L,
                remoteServerVersion = 3L,
                remoteUpdatedAtMillis = 60L,
                detectedAtMillis = 70L,
                status = ConflictStatus.OPEN.name,
                resolutionKind = null,
            ),
        )

        val persistence = createSyncForgePersistence(context, sqlDelightDbName)
        val result = RoomToSqlDelightMigrator.migrateIfNeeded(
            context = context,
            persistence = persistence,
            deleteRoomDatabaseAfterMigration = true,
        )

        assertTrue(result.performed)
        assertEquals(1, result.outboxRows)
        assertEquals(1, result.conflictRows)
        assertTrue(result.roomDatabaseDeleted)
        assertFalse(context.databaseList().contains(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME))

        val migratedOutbox = persistence.database.outboxQueries.observeAll().executeAsList()
        assertEquals(1, migratedOutbox.size)
        assertEquals("task-1", migratedOutbox.single().entityId)
        assertEquals(1L, migratedOutbox.single().retryCount)

        val migratedConflicts = persistence.database.conflictsQueries.observeAll().executeAsList()
        assertEquals(1, migratedConflicts.size)
        assertEquals("task-2", migratedConflicts.single().entityId)
        assertEquals(ConflictStatus.OPEN.name, migratedConflicts.single().status)

        val secondPass = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
        assertFalse(secondPass.performed)
    }

    @Test
    fun migrateIfNeeded_skipsWhenRoomDatabaseMissing() = runTest {
        val persistence = createSyncForgePersistence(context, sqlDelightDbName)
        val result = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
        assertFalse(result.performed)
        assertTrue(
            context.getSharedPreferences("syncforge_migration", Context.MODE_PRIVATE)
                .getBoolean("room_to_sqldelight_v1", false),
        )
    }
}