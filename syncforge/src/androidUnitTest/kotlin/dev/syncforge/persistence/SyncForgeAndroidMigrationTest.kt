package dev.syncforge.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.syncforge.SyncForge
import dev.syncforge.android
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.test.FakeEntitySyncHandler
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
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

/**
 * Verifies the [dev.syncforge.SyncForge.android] upgrade path used by [:sample]:
 * legacy Room `syncforge_outbox.db` → SQLDelight `syncforge.db` on first launch.
 */
@OptIn(ExperimentalSyncForgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncForgeAndroidMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MigrationTestSupport.resetMigrationState(context)
    }

    @After
    fun tearDown() {
        MigrationTestSupport.resetMigrationState(context)
    }

    @Test
    fun androidUpgrade_migratesLegacyRoomOutboxAndConflicts() = runTest {
        MigrationTestSupport.seedLegacyRoomStorage(
            context = context,
            outboxCount = 2,
            conflictCount = 1,
        )

        val manager = SyncForge.android(context) {
            baseUrl("http://localhost:8080")
            handler(FakeEntitySyncHandler("tasks"))
            pullPageSize = 50
            conflicts {
                entity("tasks") { deferToUser() }
            }
        }

        assertEquals(2, manager.debug.outboxItems.dropWhile { it.isEmpty() }.first().size)
        assertEquals(1, manager.debug.conflictRecords.dropWhile { it.isEmpty() }.first().size)
        assertFalse(context.databaseList().contains(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME))
        assertTrue(context.databaseList().contains(DEFAULT_DATABASE_NAME))
        assertTrue(
            context.getSharedPreferences("syncforge_migration", Context.MODE_PRIVATE)
                .getBoolean("room_to_sqldelight_v1", false),
        )
    }
}