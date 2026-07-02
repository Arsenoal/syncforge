package dev.syncforge.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalSyncForgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomToSqlDelightMigratorTest {

    private lateinit var context: Context
    private val sqlDelightDbName = "migrator_test_syncforge.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MigrationTestSupport.resetMigrationState(context, sqlDelightDbName)
    }

    @After
    fun tearDown() {
        MigrationTestSupport.resetMigrationState(context, sqlDelightDbName)
    }

    @Test
    fun migrateIfNeeded_copiesRoomOutboxAndConflicts() = runTest {
        MigrationTestSupport.seedLegacyRoomStorage(context, outboxCount = 1, conflictCount = 1)

        val persistence = createSyncForgePersistence(context, sqlDelightDbName)
        val result = RoomToSqlDelightMigrator.migrateIfNeeded(
            context = context,
            persistence = persistence,
            deleteRoomDatabaseAfterMigration = true,
        )

        assertEquals(RoomToSqlDelightMigrator.Status.SUCCESS, result.status)
        assertTrue(result.succeeded)
        assertTrue(result.performed)
        assertEquals(1, result.outboxRows)
        assertEquals(1, result.conflictRows)
        assertTrue(result.roomDatabaseDeleted)
        assertFalse(context.databaseList().contains(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME))

        val migratedOutbox = persistence.database.outboxQueries.observeAll().executeAsList()
        assertEquals(1, migratedOutbox.size)
        assertEquals("entity-0", migratedOutbox.single().entityId)

        val migratedConflicts = persistence.database.conflictsQueries.observeAll().executeAsList()
        assertEquals(1, migratedConflicts.size)
        assertEquals("conflict-0", migratedConflicts.single().entityId)
        assertEquals(ConflictStatus.OPEN.name, migratedConflicts.single().status)

        val secondPass = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
        assertEquals(RoomToSqlDelightMigrator.Status.SKIPPED_ALREADY_DONE, secondPass.status)
        assertFalse(secondPass.performed)
    }

    @Test
    fun migrateIfNeeded_skipsWhenRoomDatabaseMissing() = runTest {
        val persistence = createSyncForgePersistence(context, sqlDelightDbName)
        val result = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
        assertEquals(RoomToSqlDelightMigrator.Status.SKIPPED_NO_ROOM, result.status)
        assertFalse(result.performed)
        assertTrue(
            context.getSharedPreferences("syncforge_migration", Context.MODE_PRIVATE)
                .getBoolean("room_to_sqldelight_v1", false),
        )
    }

    @Test
    fun migrateIfNeeded_largeOutbox_migratesAllRowsInBatches() = runTest {
        val outboxCount = 250
        MigrationTestSupport.seedLegacyRoomStorage(context, outboxCount = outboxCount, conflictCount = 0)

        val persistence = createSyncForgePersistence(context, sqlDelightDbName)
        val result = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)

        assertEquals(RoomToSqlDelightMigrator.Status.SUCCESS, result.status)
        assertEquals(outboxCount, result.outboxRows)
        assertEquals(
            outboxCount,
            persistence.database.outboxQueries.observeAll().executeAsList().size,
        )
        assertEquals(
            outboxCount.toLong(),
            persistence.database.outboxQueries.maxOutboxId().executeAsOne().MAX,
        )
    }

    @Test
    fun migrateIfNeeded_partialFailure_retriesAndCompletes() = runTest {
        val outboxCount = 150
        MigrationTestSupport.seedLegacyRoomStorage(context, outboxCount = outboxCount, conflictCount = 2)

        val persistence = createSyncForgePersistence(context, sqlDelightDbName)
        // Fail in batch 2 so batch 1 (100 rows) is committed before rollback.
        RoomToSqlDelightMigrator.failAfterOutboxRows = 125

        val failed = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
        assertEquals(RoomToSqlDelightMigrator.Status.FAILED, failed.status)
        assertFalse(failed.succeeded)
        assertEquals(100, failed.outboxRows)
        assertFalse(
            context.getSharedPreferences("syncforge_migration", Context.MODE_PRIVATE)
                .getBoolean("room_to_sqldelight_v1", false),
        )
        assertTrue(context.databaseList().contains(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME))

        val recovered = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
        assertEquals(RoomToSqlDelightMigrator.Status.SUCCESS, recovered.status)
        assertEquals(outboxCount, recovered.outboxRows)
        assertEquals(2, recovered.conflictRows)
        assertNull(recovered.errorMessage)
        assertEquals(
            outboxCount,
            persistence.database.outboxQueries.observeAll().executeAsList().size,
        )
        assertEquals(
            2,
            persistence.database.conflictsQueries.observeAll().executeAsList().size,
        )
    }

}