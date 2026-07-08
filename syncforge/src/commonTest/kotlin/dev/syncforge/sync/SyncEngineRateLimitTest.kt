package dev.syncforge.sync

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.model.SyncResult
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.SyncTransport
import dev.syncforge.network.SyncTransportException
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.test.FakeEntitySyncHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SyncEngineRateLimitTest {

    @Test
    fun runPush_preservesTransportErrorCodeAndRetryAfter() = runTest {
        var now = 1_000L
        val outbox = InMemoryOutboxRepository().apply {
            clock = { now }
            setMaxRetriesForObservation(maxRetries = 5)
        }
        val handler = FakeEntitySyncHandler("tasks")
        val transport = RateLimitedPushTransport(retryAfterMillis = 60_000L)
        val engine = engine(
            outbox = outbox,
            transport = transport,
            handler = handler,
            clock = { now },
            backoffPolicy = SyncBackoffPolicy(
                strategy = SyncBackoffPolicy.Strategy.FIXED,
                baseDelay = 5.seconds,
            ),
        )

        outbox.enqueueEntry(entityId = "t1")
        val result = engine.runPush()

        assertTrue(result is SyncResult.Failure)
        result as SyncResult.Failure
        assertEquals(SyncError.Code.SERVER, result.error.code)
        assertEquals(429, result.error.httpStatus)
        assertEquals(60_000L, result.error.retryAfterMillis)

        val stored = outbox.findForEntity("tasks", "t1").single()
        assertEquals(1, stored.retryCount)
        assertEquals(61_000L, stored.nextRetryAtMillis)
    }

    @Test
    fun runPull_unwrapsSyncTransportException() = runTest {
        val transport = RateLimitedPullTransport()
        val engine = engine(
            outbox = InMemoryOutboxRepository(),
            transport = transport,
            handler = FakeEntitySyncHandler("tasks"),
        )

        val result = engine.runPull(sinceTimestampMillis = 0L)

        assertTrue(result is SyncResult.Failure)
        result as SyncResult.Failure
        assertEquals(SyncError.Code.SERVER, result.error.code)
        assertEquals(30_000L, result.error.retryAfterMillis)
    }

    private fun engine(
        outbox: InMemoryOutboxRepository,
        transport: SyncTransport,
        handler: FakeEntitySyncHandler,
        clock: () -> Long = { 1_000L },
        backoffPolicy: SyncBackoffPolicy = SyncBackoffPolicy.Default,
    ): SyncEngine =
        SyncEngine(
            config = SyncConfig(
                entityTypes = setOf("tasks"),
                backoffPolicy = backoffPolicy,
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

    private class RateLimitedPushTransport(
        private val retryAfterMillis: Long,
    ) : SyncTransport {
        override suspend fun push(entries: List<OutboxEntry>): PushResult {
            throw SyncTransportException(
                SyncError(
                    code = SyncError.Code.SERVER,
                    message = "HTTP 429",
                    httpStatus = 429,
                    retryAfterMillis = retryAfterMillis,
                ),
            )
        }

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = sinceTimestampMillis)
    }

    private class RateLimitedPullTransport : SyncTransport {
        override suspend fun push(entries: List<OutboxEntry>): PushResult =
            PushResult(acknowledgedIds = entries.map { it.id })

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult {
            throw SyncTransportException(
                SyncError(
                    code = SyncError.Code.SERVER,
                    message = "HTTP 429",
                    httpStatus = 429,
                    retryAfterMillis = 30_000L,
                ),
            )
        }
    }
}