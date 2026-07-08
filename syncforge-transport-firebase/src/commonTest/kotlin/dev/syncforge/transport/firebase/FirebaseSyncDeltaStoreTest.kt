package dev.syncforge.transport.firebase

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.RemoteDeltaDto
import dev.syncforge.transport.DeltaStoreSyncTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class FirebaseSyncDeltaStoreTest {

    @Test
    fun appendEntries_delegatesToFirebaseSyncApi() = runTest {
        val api = RecordingFirebaseSyncApi(
            pushResponse = PushResponse(acknowledgedIds = listOf(5L)),
        )
        val store = FirebaseSyncDeltaStore(api) { 1_500L }

        val result = store.appendEntries(
            listOf(
                OutboxEntry(
                    id = 5L,
                    entityType = "tasks",
                    entityId = "task-5",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"title":"Firebase"}""",
                    localVersion = 1L,
                    createdAtMillis = 10L,
                ),
            ),
        )

        assertEquals(listOf(5L), result.acknowledgedIds)
        assertEquals(1_500L, api.pushCalls.single().nowMillis)
    }

    @Test
    fun queryDeltas_delegatesPullParameters() = runTest {
        val api = RecordingFirebaseSyncApi(
            pullResponse = PullResponse(deltas = emptyList(), serverTimestampMillis = 2_500L),
        )
        val store = FirebaseSyncDeltaStore(api) { 2_500L }

        store.queryDeltas(
            sinceTimestampMillis = 75L,
            entityTypes = setOf("tags"),
            pageSize = 20,
            pageCursor = "cursor-b",
        )

        val call = api.pullCalls.single()
        assertEquals(75L, call.sinceTimestampMillis)
        assertEquals(setOf("tags"), call.entityTypes)
        assertEquals(20, call.pageSize)
        assertEquals("cursor-b", call.pageCursor)
        assertEquals(2_500L, call.nowMillis)
    }

    @Test
    fun deltaStoreSyncTransport_roundTrip() = runTest {
        val api = RecordingFirebaseSyncApi(
            pushResponse = PushResponse(acknowledgedIds = listOf(2L)),
            pullResponse = PullResponse(
                deltas = listOf(
                    RemoteDeltaDto(
                        entityType = "notes",
                        entityId = "note-2",
                        payloadJson = """{"body":"Remote"}""",
                        serverVersion = 3L,
                        updatedAtMillis = 800L,
                    ),
                ),
                serverTimestampMillis = 900L,
            ),
        )
        val transport = DeltaStoreSyncTransport(FirebaseSyncDeltaStore(api))

        val push = transport.push(
            listOf(
                OutboxEntry(
                    id = 2L,
                    entityType = "notes",
                    entityId = "note-2",
                    changeType = ChangeType.UPDATE,
                    payloadJson = """{"body":"Local"}""",
                    localVersion = 3L,
                    createdAtMillis = 100L,
                ),
            ),
        )
        assertEquals(listOf(2L), push.acknowledgedIds)

        val pull = transport.pull(sinceTimestampMillis = 0L, entityTypes = setOf("notes"))
        assertEquals(1, pull.deltas.size)
        assertEquals("note-2", pull.deltas.single().entityId)
    }

    private class RecordingFirebaseSyncApi(
        private val pushResponse: PushResponse = PushResponse(),
        private val pullResponse: PullResponse = PullResponse(deltas = emptyList(), serverTimestampMillis = 0L),
    ) : FirebaseSyncApi {

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