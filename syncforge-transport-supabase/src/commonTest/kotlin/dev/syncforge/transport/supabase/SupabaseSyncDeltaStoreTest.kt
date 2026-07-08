package dev.syncforge.transport.supabase

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.RemoteDeltaDto
import dev.syncforge.transport.DeltaStoreSyncTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SupabaseSyncDeltaStoreTest {

    @Test
    fun appendEntries_delegatesToSupabaseSyncApi() = runTest {
        val api = RecordingSupabaseSyncApi(
            pushResponse = PushResponse(acknowledgedIds = listOf(3L)),
        )
        val store = SupabaseSyncDeltaStore(api) { 1_000L }

        val result = store.appendEntries(
            listOf(
                OutboxEntry(
                    id = 3L,
                    entityType = "tasks",
                    entityId = "task-3",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"title":"Supabase"}""",
                    localVersion = 1L,
                    createdAtMillis = 10L,
                ),
            ),
        )

        assertEquals(listOf(3L), result.acknowledgedIds)
        assertEquals(1_000L, api.pushCalls.single().nowMillis)
    }

    @Test
    fun queryDeltas_delegatesPullParameters() = runTest {
        val api = RecordingSupabaseSyncApi(
            pullResponse = PullResponse(deltas = emptyList(), serverTimestampMillis = 2_000L),
        )
        val store = SupabaseSyncDeltaStore(api) { 2_000L }

        store.queryDeltas(
            sinceTimestampMillis = 50L,
            entityTypes = setOf("notes"),
            pageSize = 10,
            pageCursor = "cursor-a",
        )

        val call = api.pullCalls.single()
        assertEquals(50L, call.sinceTimestampMillis)
        assertEquals(setOf("notes"), call.entityTypes)
        assertEquals(10, call.pageSize)
        assertEquals("cursor-a", call.pageCursor)
        assertEquals(2_000L, call.nowMillis)
    }

    @Test
    fun deltaStoreSyncTransport_roundTrip() = runTest {
        val api = RecordingSupabaseSyncApi(
            pushResponse = PushResponse(acknowledgedIds = listOf(1L)),
            pullResponse = PullResponse(
                deltas = listOf(
                    RemoteDeltaDto(
                        entityType = "tasks",
                        entityId = "task-1",
                        payloadJson = """{"title":"Remote"}""",
                        serverVersion = 2L,
                        updatedAtMillis = 500L,
                    ),
                ),
                serverTimestampMillis = 600L,
            ),
        )
        val transport = DeltaStoreSyncTransport(SupabaseSyncDeltaStore(api))

        val push = transport.push(
            listOf(
                OutboxEntry(
                    id = 1L,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.UPDATE,
                    payloadJson = """{"title":"Local"}""",
                    localVersion = 2L,
                    createdAtMillis = 100L,
                ),
            ),
        )
        assertEquals(listOf(1L), push.acknowledgedIds)

        val pull = transport.pull(sinceTimestampMillis = 0L, entityTypes = setOf("tasks"))
        assertEquals(1, pull.deltas.size)
        assertEquals("task-1", pull.deltas.single().entityId)
    }

    private class RecordingSupabaseSyncApi(
        private val pushResponse: PushResponse = PushResponse(),
        private val pullResponse: PullResponse = PullResponse(deltas = emptyList(), serverTimestampMillis = 0L),
    ) : SupabaseSyncApi {

        data class PushCall(val entries: List<OutboxEntry>, val nowMillis: Long)
        data class PullCall(
            val sinceTimestampMillis: Long,
            val entityTypes: Set<String>,
            val pageSize: Int,
            val pageCursor: String?,
            val nowMillis: Long,
        )

        val pushCalls = mutableListOf<PushCall>()
        val pullCalls = mutableListOf<PullCall>()

        override suspend fun push(entries: List<OutboxEntry>, nowMillis: Long): PushResponse {
            pushCalls += PushCall(entries, nowMillis)
            return pushResponse
        }

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
            nowMillis: Long,
        ): PullResponse {
            pullCalls += PullCall(sinceTimestampMillis, entityTypes, pageSize, pageCursor, nowMillis)
            return pullResponse
        }
    }
}