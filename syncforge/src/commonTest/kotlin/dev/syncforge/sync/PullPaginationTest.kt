package dev.syncforge.sync

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.SyncResult
import dev.syncforge.network.PullResult
import dev.syncforge.network.RemoteDelta
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.test.FakeEntitySyncHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class PullPaginationTest {

    private class PagedTransport : SyncTransport {
        var callCount = 0

        override suspend fun push(entries: List<dev.syncforge.model.OutboxEntry>) =
            dev.syncforge.network.PushResult(acknowledgedIds = entries.map { it.id })

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult {
            callCount++
            return when (callCount) {
                1 -> PullResult(
                    deltas = listOf(delta("1")),
                    serverTimestampMillis = 100L,
                    hasMore = true,
                    nextPageCursor = "page-2",
                )
                else -> PullResult(
                    deltas = listOf(delta("2")),
                    serverTimestampMillis = 200L,
                    hasMore = false,
                )
            }
        }

        private fun delta(id: String) = RemoteDelta(
            entityType = "tasks",
            entityId = id,
            payloadJson = null,
            serverVersion = 1,
            updatedAtMillis = 100L,
        )
    }

    @Test
    fun runPull_fetchesAllPages() = kotlinx.coroutines.test.runTest {
        val transport = PagedTransport()
        val engine = SyncEngine(
            config = SyncConfig(entityTypes = setOf("tasks"), pullPageSize = 1),
            outbox = InMemoryOutboxRepository(),
            transport = transport,
            registry = EntityRegistry(listOf(FakeEntitySyncHandler("tasks"))),
        )

        val result = engine.runPull(0L)
        assertEquals(2, transport.callCount)
        assertEquals(2, (result as SyncResult.Success).pulled)
        assertEquals(200L, result.syncCursorMillis)
    }
}