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
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.RemoteDelta
import dev.syncforge.persistence.MigrationTestSupport
import dev.syncforge.persistence.MigrationTestSupport.INSTRUMENTED_TEST_DATABASE_NAME
import dev.syncforge.persistence.RoomToSqlDelightMigrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
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
 * Instrumented upgrade test on the [:sample] configuration path (custom DB name,
 * multi-entity conflict policies). Runs on emulator/device — included in `./gradlew androidE2e`.
 *
 * Uses [INSTRUMENTED_TEST_DATABASE_NAME] so setup does not delete the live `syncforge.db`
 * opened by [dev.syncforge.sample.SampleApplication].
 */
@OptIn(ExperimentalSyncForgeApi::class)
@RunWith(AndroidJUnit4::class)
class RoomMigrationInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun setUp() {
        MigrationTestSupport.resetMigrationState(context, INSTRUMENTED_TEST_DATABASE_NAME)
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
            databaseName(INSTRUMENTED_TEST_DATABASE_NAME)
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
        assertTrue(context.databaseList().contains(INSTRUMENTED_TEST_DATABASE_NAME))
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
            databaseName(INSTRUMENTED_TEST_DATABASE_NAME)
            baseUrl("http://10.0.2.2:8080")
            handler(MinimalTaskHandler())
            conflicts {
                entity("tasks") { deferToUser() }
            }
            // Avoid reconnect-triggered auto-push racing the explicit push() below.
            customize {
                networkMonitor = QuietNetworkMonitor
            }
        }

        assertEquals(1, manager.debug.outboxItems.dropWhile { it.isEmpty() }.first().size)

        val result = manager.push()
        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).pushed)
        assertEquals(0, manager.debug.outboxItems.dropWhile { it.isNotEmpty() }.first().size)
    }

    /** Online for manual push(), but does not subscribe to connectivity callbacks. */
    private object QuietNetworkMonitor : NetworkMonitor {
        override val isOnline: Boolean = true
        override fun observeOnline(): Flow<Boolean> = emptyFlow()
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