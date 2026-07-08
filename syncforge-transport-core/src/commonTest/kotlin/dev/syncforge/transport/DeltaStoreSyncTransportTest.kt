package dev.syncforge.transport

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.RemoteDelta
import dev.syncforge.network.SyncTransportException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class DeltaStoreSyncTransportTest {

    @Test
    fun push_delegatesToSyncDeltaStore() = runTest {
        val store = RecordingSyncDeltaStore(pushResult = PushResult(acknowledgedIds = listOf(7L)))
        val transport = DeltaStoreSyncTransport(store)

        val result = transport.push(
            listOf(
                OutboxEntry(
                    id = 7L,
                    entityType = "tasks",
                    entityId = "t-7",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"title":"Hi"}""",
                    localVersion = 1L,
                    createdAtMillis = 10L,
                ),
            ),
        )

        assertEquals(listOf(7L), result.acknowledgedIds)
        assertEquals(1, store.appendCalls.size)
        assertEquals(7L, store.appendCalls.single().single().id)
    }

    @Test
    fun pull_delegatesQueryParameters() = runTest {
        val store = RecordingSyncDeltaStore(
            pullResult = PullResult(deltas = emptyList(), serverTimestampMillis = 500L),
        )
        val transport = DeltaStoreSyncTransport(store)

        transport.pull(
            sinceTimestampMillis = 100L,
            entityTypes = setOf("notes", "tasks"),
            pageSize = 50,
            pageCursor = "page-2",
        )

        val call = store.queryCalls.single()
        assertEquals(100L, call.sinceTimestampMillis)
        assertEquals(setOf("notes", "tasks"), call.entityTypes)
        assertEquals(50, call.pageSize)
        assertEquals("page-2", call.pageCursor)
    }

    @Test
    fun push_wrapsUnexpectedStoreErrorsAsNetworkSyncTransportException() = runTest {
        val store = object : SyncDeltaStore {
            override suspend fun appendEntries(entries: List<OutboxEntry>): PushResult =
                error("firestore unavailable")

            override suspend fun queryDeltas(
                sinceTimestampMillis: Long,
                entityTypes: Set<String>,
                pageSize: Int,
                pageCursor: String?,
            ): PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = 0L)
        }
        val transport = DeltaStoreSyncTransport(store)

        val ex = assertFailsWith<SyncTransportException> {
            transport.push(emptyList())
        }
        assertEquals(SyncError.Code.NETWORK, ex.error.code)
    }

    private class RecordingSyncDeltaStore(
        private val pushResult: PushResult = PushResult(acknowledgedIds = emptyList()),
        private val pullResult: PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = 0L),
    ) : SyncDeltaStore {

        val appendCalls = mutableListOf<List<OutboxEntry>>()
        val queryCalls = mutableListOf<QueryCall>()

        data class QueryCall(
            val sinceTimestampMillis: Long,
            val entityTypes: Set<String>,
            val pageSize: Int,
            val pageCursor: String?,
        )

        override suspend fun appendEntries(entries: List<OutboxEntry>): PushResult {
            appendCalls += entries
            return pushResult
        }

        override suspend fun queryDeltas(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult {
            queryCalls += QueryCall(sinceTimestampMillis, entityTypes, pageSize, pageCursor)
            return pullResult
        }
    }
}