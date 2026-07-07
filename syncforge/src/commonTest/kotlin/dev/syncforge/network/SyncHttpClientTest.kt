package dev.syncforge.network

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PushRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncHttpClientTest {

    @Test
    fun fakeRecordsPostPushWithBaseUrlAndRequest() = runTest {
        val client = FakeSyncHttpClient(
            pushResult = PushResult(acknowledgedIds = listOf(7L)),
        )
        val request = PushRequest(
            entries = listOf(
                OutboxEntryDto(
                    id = 7L,
                    entityType = "tasks",
                    entityId = "t1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"t1"}""",
                    localVersion = 1L,
                    createdAtMillis = 100L,
                ),
            ),
        )

        val result = client.postPush("https://api.example.com/", request)

        assertEquals(listOf(7L), result.acknowledgedIds)
        assertEquals(1, client.pushCalls.size)
        assertEquals("https://api.example.com/", client.pushCalls.single().baseUrl)
        assertEquals(request, client.pushCalls.single().request)
    }

    @Test
    fun fakeRecordsGetPullWithQueryParams() = kotlinx.coroutines.test.runTest {
        val delta = RemoteDelta(
            entityType = "notes",
            entityId = "n1",
            payloadJson = """{"id":"n1"}""",
            serverVersion = 2L,
            updatedAtMillis = 200L,
        )
        val client = FakeSyncHttpClient(
            pullResult = PullResult(
                deltas = listOf(delta),
                serverTimestampMillis = 200L,
                hasMore = true,
                nextPageCursor = "page-2",
            ),
        )
        val params = PullQueryParams(
            sinceTimestampMillis = 100L,
            entityTypes = setOf("tasks", "notes"),
            pageSize = 50,
            pageCursor = "page-1",
        )

        val result = client.getPull("https://api.example.com", params)

        assertEquals(listOf(delta), result.deltas)
        assertTrue(result.hasMore)
        assertEquals("page-2", result.nextPageCursor)
        assertEquals(1, client.pullCalls.size)
        assertEquals("https://api.example.com", client.pullCalls.single().baseUrl)
        assertEquals(params, client.pullCalls.single().params)
    }

    @Test
    fun syncHttpRoutesMatchRestContract() {
        assertEquals("/sync/push", SyncHttpRoutes.PUSH_PATH)
        assertEquals("/sync/pull", SyncHttpRoutes.PULL_PATH)
    }
}