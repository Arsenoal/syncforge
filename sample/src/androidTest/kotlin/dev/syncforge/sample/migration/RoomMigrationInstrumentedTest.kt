package dev.syncforge.sample.migration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.syncforge.SyncForge
import dev.syncforge.android
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.EntitySyncHandler
import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncResult
import dev.syncforge.network.RemoteDelta
import dev.syncforge.persistence.MigrationTestSupport
import dev.syncforge.persistence.RoomToSqlDelightMigrator
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Instrumented upgrade test on the [:sample] configuration path (default `syncforge.db`,
 * multi-entity conflict policies). Runs on emulator/device — included in `./gradlew androidE2e`.
 */
@OptIn(ExperimentalSyncForgeApi::class)
@RunWith(AndroidJUnit4::class)
class RoomMigrationInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun setUp() {
        MigrationTestSupport.resetMigrationState(context)
    }

    @Test
    fun upgradeFromRoom_preservesPendingOutboxLikeSampleApp() = runBlocking {
        MigrationTestSupport.seedLegacyRoomStorage(
            context = context,
            outboxCount = 2,
            conflictCount = 1,
            entityType = "tasks",
        )

        val manager = SyncForge.android(context) {
            baseUrl("http://10.0.2.2:8080")
            handler(MinimalTaskHandler())
            pullPageSize = 50
            conflicts {
                entity("tasks") { deferToUser() }
                entity("notes") { lastWriteWins() }
                entity("tags") { lastWriteWins() }
            }
        }

        assertEquals(2, manager.debug.outboxItems.dropWhile { it.isEmpty() }.first().size)
        assertEquals(1, manager.debug.conflictRecords.dropWhile { it.isEmpty() }.first().size)
        assertFalse(context.databaseList().contains(RoomToSqlDelightMigrator.ROOM_DATABASE_NAME))
        assertTrue(context.databaseList().contains("syncforge.db"))
    }

    @Test
    fun upgradeFromRoom_pendingOutboxCanPushToMockServer() = runBlocking {
        Assume.assumeTrue("Mock server required for push verification", isMockServerHealthy())

        MigrationTestSupport.seedLegacyRoomStorage(
            context = context,
            outboxCount = 1,
            entityType = "tasks",
        )

        val manager = SyncForge.android(context) {
            baseUrl("http://10.0.2.2:8080")
            handler(MinimalTaskHandler())
            conflicts {
                entity("tasks") { deferToUser() }
            }
        }

        assertEquals(1, manager.debug.outboxItems.dropWhile { it.isEmpty() }.first().size)

        val result = manager.push()
        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).pushed)
        assertEquals(0, manager.debug.outboxItems.dropWhile { it.isNotEmpty() }.first().size)
    }

    private fun isMockServerHealthy(): Boolean =
        runCatching {
            val url = URL("http://10.0.2.2:8080/health")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
                requestMethod = "GET"
            }
            connection.responseCode == 200
        }.getOrDefault(false)

    private class MinimalTaskHandler : EntitySyncHandler {
        override val entityType: String = "tasks"

        override suspend fun captureSnapshot(entityId: String): String? = null

        override suspend fun applyOptimistic(change: Change<*>) = Unit

        override suspend fun rollbackEntry(entry: OutboxEntry) = Unit

        override suspend fun onPushAcknowledged(entryEntityId: String) = Unit

        override suspend fun applyPullDelta(delta: RemoteDelta): PullApplyOutcome = PullApplyOutcome.SKIPPED

        override fun serializeChange(change: Change<*>): String? = """{"id":"migrated"}"""
    }
}