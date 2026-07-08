@file:OptIn(dev.syncforge.api.ExperimentalSyncForgeApi::class)

package dev.syncforge.sync

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncState
import dev.syncforge.network.NoOpSyncTransport
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.test.FakeEntitySyncHandler
import dev.syncforge.trace.RecordingSyncTracer
import dev.syncforge.trace.SyncSpanName
import dev.syncforge.trace.SyncTraceAttributes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineTracingTest {

    @Test
    fun push_emitsPushSpanWithBatchSize() = runTest {
        val tracer = RecordingSyncTracer()
        val outbox = InMemoryOutboxRepository()
        outbox.enqueueEntry(entityId = "t1")
        val transport = object : SyncTransport {
            override suspend fun push(entries: List<OutboxEntry>) =
                PushResult(acknowledgedIds = entries.map { it.id })

            override suspend fun pull(
                sinceTimestampMillis: Long,
                entityTypes: Set<String>,
                pageSize: Int,
                pageCursor: String?,
            ) = PullResult(deltas = emptyList(), serverTimestampMillis = 0L)
        }
        val engine = engine(outbox, transport, tracer = tracer)

        val result = engine.runPush()

        assertTrue(result is SyncResult.Success)
        assertEquals(1, tracer.spans.size)
        assertEquals(SyncSpanName.PUSH, tracer.spans.single().name)
        assertEquals("1", tracer.spans.single().attributes[SyncTraceAttributes.BATCH_SIZE])
    }

    @Test
    fun push_withDisabledTracer_emitsNoSpans() = runTest {
        var startCount = 0
        val tracer = object : dev.syncforge.trace.SyncTracer {
            override val isEnabled: Boolean = false
            override fun startSpan(
                name: dev.syncforge.trace.SyncSpanName,
                attributes: Map<String, String>,
            ): dev.syncforge.trace.SyncSpan {
                startCount++
                error("must not start span")
            }
        }
        val outbox = InMemoryOutboxRepository()
        outbox.enqueueEntry(entityId = "t2")
        val engine = engine(outbox, NoOpSyncTransport, tracer = tracer)

        engine.runPush()

        assertEquals(0, startCount)
    }

    private fun engine(
        outbox: InMemoryOutboxRepository,
        transport: SyncTransport,
        tracer: dev.syncforge.trace.SyncTracer,
    ): SyncEngine =
        SyncEngine(
            config = SyncConfig(entityTypes = setOf("tasks")),
            outbox = outbox,
            transport = transport,
            registry = EntityRegistry(listOf(FakeEntitySyncHandler("tasks"))),
            tracer = tracer,
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
        override val syncState: SyncState = SyncState.PENDING,
    ) : dev.syncforge.entity.SyncedEntity
}