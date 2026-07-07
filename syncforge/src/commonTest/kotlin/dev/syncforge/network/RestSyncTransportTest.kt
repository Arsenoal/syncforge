package dev.syncforge.network

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.api.PushRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class RestSyncTransportTest {

    @Test
    fun push_mapsOutboxEntriesToHttpClientRequest() = runTest {
        val fake = FakeSyncHttpClient(pushResult = PushResult(acknowledgedIds = listOf(9L)))
        val transport = RestSyncTransport("https://api.example.com/", fake)

        val result = transport.push(
            listOf(
                OutboxEntry(
                    id = 9L,
                    entityType = "tasks",
                    entityId = "t1",
                    changeType = ChangeType.UPDATE,
                    payloadJson = """{"id":"t1"}""",
                    localVersion = 2L,
                    createdAtMillis = 50L,
                ),
            ),
        )

        assertEquals(listOf(9L), result.acknowledgedIds)
        assertEquals(1, fake.pushCalls.size)
        assertEquals("https://api.example.com", fake.pushCalls.single().baseUrl)
        assertEquals(1, fake.pushCalls.single().request.entries.size)
        assertEquals(9L, fake.pushCalls.single().request.entries.single().id)
    }

    @Test
    fun pull_buildsPullQueryParams() = runTest {
        val fake = FakeSyncHttpClient(
            pullResult = PullResult(deltas = emptyList(), serverTimestampMillis = 300L),
        )
        val transport = RestSyncTransport("https://api.example.com", fake)

        transport.pull(
            sinceTimestampMillis = 100L,
            entityTypes = setOf("tasks", "notes"),
            pageSize = 25,
            pageCursor = "c1",
        )

        val params = fake.pullCalls.single().params
        assertEquals(100L, params.sinceTimestampMillis)
        assertEquals(setOf("tasks", "notes"), params.entityTypes)
        assertEquals(25, params.pageSize)
        assertEquals("c1", params.pageCursor)
    }

    @Test
    fun push_wrapsUnexpectedErrorsAsNetworkSyncTransportException() = runTest {
        val failingClient = object : SyncHttpClient {
            override suspend fun postPush(baseUrl: String, request: PushRequest): PushResult =
                error("socket closed")

            override suspend fun getPull(baseUrl: String, params: PullQueryParams): PullResult =
                PullResult(deltas = emptyList(), serverTimestampMillis = 0L)
        }
        val transport = RestSyncTransport("https://api.example.com", failingClient)

        val ex = assertFailsWith<SyncTransportException> {
            transport.push(emptyList())
        }
        assertEquals(SyncError.Code.NETWORK, ex.error.code)
    }
}