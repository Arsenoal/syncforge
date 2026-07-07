package dev.syncforge.sync

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncStatus
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.RemoteDelta
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.test.FakeEntitySyncHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end [SyncEngine] scenarios: retry exhaustion, multi-page pull, offline queue.
 * Covers 1.0-P1-04 integration test requirements.
 */
class SyncEngineIntegrationTest {

    @Test
    fun runPush_exhaustsRetries_thenSurfacesPermanentFailure() = runTest {
        var now = 1_000L
        val outbox = InMemoryOutboxRepository().apply {
            clock = { now }
            setMaxRetriesForObservation(maxRetries = 3)
        }
        val handler = FakeEntitySyncHandler("tasks")
        val transport = FailingPushTransport()
        val engine = engine(outbox, transport, handler, maxRetries = 3, clock = { now })

        outbox.enqueueEntry(entityId = "t1")
        repeat(3) {
            val result = engine.runPush()
            assertTrue(result is SyncResult.Failure)
            now = Long.MAX_VALUE
        }

        assertEquals(0, outbox.peek(limit = 10, nowMillis = Long.MAX_VALUE).size)
        assertEquals(1, outbox.countPermanentlyFailed(maxRetries = 3))
        val status = engine.resolveStatus(networkOnline = true, lastSyncedAt = null)
        assertTrue(status is SyncStatus.Error)
        assertEquals(false, (status as SyncStatus.Error).retryable)
    }

    @Test
    fun runPull_fetchesEveryPage_andAppliesAllDeltas() = runTest {
        val transport = PagedPullTransport(
            pages = listOf(
                PullResult(
                    deltas = listOf(delta("a")),
                    serverTimestampMillis = 100L,
                    hasMore = true,
                    nextPageCursor = "p2",
                ),
                PullResult(
                    deltas = listOf(delta("b")),
                    serverTimestampMillis = 200L,
                    hasMore = true,
                    nextPageCursor = "p3",
                ),
                PullResult(
                    deltas = listOf(delta("c")),
                    serverTimestampMillis = 300L,
                    hasMore = false,
                ),
            ),
        )
        val handler = FakeEntitySyncHandler("tasks")
        val engine = engine(InMemoryOutboxRepository(), transport, handler, pullPageSize = 1)

        val result = engine.runPull(sinceTimestampMillis = 0L)

        assertEquals(3, transport.pullCalls)
        assertTrue(result is SyncResult.Success)
        result as SyncResult.Success
        assertEquals(3, result.pulled)
        assertEquals(300L, result.syncCursorMillis)
        assertEquals(listOf("a", "b", "c"), handler.pullDeltas.map { it.entityId })
    }

    @Test
    fun offlineQueue_retainsEntries_andReportsOfflineUntilPushSucceeds() = runTest {
        var now = 5_000L
        val outbox = InMemoryOutboxRepository().apply {
            clock = { now }
            setMaxRetriesForObservation(maxRetries = 5)
        }
        val handler = FakeEntitySyncHandler("tasks")
        val transport = TogglePushTransport()
        val engine = engine(outbox, transport, handler, clock = { now })

        outbox.enqueueEntry(entityId = "offline-1")

        val offlineStatus = engine.resolveStatus(networkOnline = false, lastSyncedAt = null)
        assertEquals(SyncStatus.Offline(outboxCount = 1), offlineStatus)

        transport.pushShouldFail = true
        val failed = engine.runPush()
        assertTrue(failed is SyncResult.Failure)
        assertEquals(1, outbox.countAwaitingPush())

        val stillOffline = engine.resolveStatus(networkOnline = false, lastSyncedAt = null)
        assertEquals(SyncStatus.Offline(outboxCount = 1), stillOffline)

        transport.pushShouldFail = false
        now = Long.MAX_VALUE
        val succeeded = engine.runPush()
        assertTrue(succeeded is SyncResult.Success)
        assertEquals(1, (succeeded as SyncResult.Success).pushed)
        assertEquals(listOf("offline-1"), handler.acknowledgedIds)
        assertEquals(0, outbox.countAwaitingPush())
        assertEquals(SyncStatus.Idle, engine.resolveStatus(networkOnline = true, lastSyncedAt = null))
    }

    private fun engine(
        outbox: InMemoryOutboxRepository,
        transport: SyncTransport,
        handler: FakeEntitySyncHandler,
        maxRetries: Int = 5,
        pullPageSize: Int = 100,
        clock: () -> Long = { 0L },
    ): SyncEngine =
        SyncEngine(
            config = SyncConfig(
                entityTypes = setOf("tasks"),
                maxRetries = maxRetries,
                pullPageSize = pullPageSize,
            ),
            outbox = outbox,
            transport = transport,
            registry = EntityRegistry(listOf(handler)),
            clock = clock,
        )

    private suspend fun InMemoryOutboxRepository.enqueueEntry(entityId: String): OutboxEntry =
        enqueue(
            change = dev.syncforge.model.Change.create("tasks", StubTask(entityId)),
            payloadJson = """{"id":"$entityId"}""",
            rollbackSnapshotJson = null,
        )

    private data class StubTask(
        override val id: String,
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: dev.syncforge.model.SyncState = dev.syncforge.model.SyncState.PENDING,
    ) : dev.syncforge.entity.SyncedEntity

    private fun delta(id: String) = RemoteDelta(
        entityType = "tasks",
        entityId = id,
        payloadJson = null,
        serverVersion = 1,
        updatedAtMillis = 100L,
    )

    private class FailingPushTransport : SyncTransport {
        override suspend fun push(entries: List<OutboxEntry>): PushResult =
            error("network unavailable")

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = sinceTimestampMillis)
    }

    private class TogglePushTransport : SyncTransport {
        var pushShouldFail: Boolean = false

        override suspend fun push(entries: List<OutboxEntry>): PushResult {
            if (pushShouldFail) error("network unavailable")
            return PushResult(acknowledgedIds = entries.map { it.id })
        }

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = sinceTimestampMillis)
    }

    private class PagedPullTransport(
        private val pages: List<PullResult>,
    ) : SyncTransport {
        var pullCalls = 0

        override suspend fun push(entries: List<OutboxEntry>): PushResult =
            PushResult(acknowledgedIds = entries.map { it.id })

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult {
            val page = pages[pullCalls]
            pullCalls++
            return page
        }
    }
}