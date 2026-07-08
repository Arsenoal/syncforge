package dev.syncforge.transport.graphql

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.SyncTransportException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class KtorGraphQlSyncApiTest {

    @Test
    fun push_sendsGraphQlMutationAndParsesResponse() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "data": {
                        "syncPush": {
                          "acknowledgedIds": [42],
                          "rejected": []
                        }
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(KtorGraphQlSyncApi.defaultJson) }
        }
        val api = KtorGraphQlSyncApi(
            config = GraphQlSyncConfig(
                endpointUrl = "http://localhost:8080/graphql",
                bearerToken = { "graphql-token" },
            ),
            httpClient = client,
        )

        val result = api.push(
            listOf(
                OutboxEntry(
                    id = 42L,
                    entityType = "tasks",
                    entityId = "t1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"t1","title":"Test"}""",
                    localVersion = 1L,
                    createdAtMillis = 100L,
                ),
            ),
        )

        assertEquals(listOf(42L), result.acknowledgedIds)
        assertTrue(result.rejected.isEmpty())
    }

    @Test
    fun pull_sendsGraphQlQueryAndParsesDeltas() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "data": {
                        "syncPull": {
                          "deltas": [{
                            "entityType": "tasks",
                            "entityId": "t1",
                            "payloadJson": "{\"id\":\"t1\",\"title\":\"Remote\"}",
                            "serverVersion": 2,
                            "updatedAtMillis": 200,
                            "isDeleted": false
                          }],
                          "serverTimestampMillis": 250,
                          "hasMore": false,
                          "nextPageCursor": null
                        }
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(KtorGraphQlSyncApi.defaultJson) }
        }
        val api = KtorGraphQlSyncApi(
            config = GraphQlSyncConfig(endpointUrl = "http://localhost:8080/graphql"),
            httpClient = client,
        )

        val result = api.pull(
            sinceTimestampMillis = 100L,
            entityTypes = setOf("tasks", "notes"),
            pageSize = Int.MAX_VALUE,
            pageCursor = null,
        )

        assertEquals(1, result.deltas.size)
        assertEquals("t1", result.deltas.first().entityId)
        assertEquals(250L, result.serverTimestampMillis)
    }

    @Test
    fun graphqlErrors_mapToSyncTransportException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"errors":[{"message":"Unknown operation"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(KtorGraphQlSyncApi.defaultJson) }
        }
        val api = KtorGraphQlSyncApi(
            config = GraphQlSyncConfig(endpointUrl = "http://localhost:8080/graphql"),
            httpClient = client,
        )

        val ex = assertFailsWith<SyncTransportException> {
            api.push(emptyList())
        }
        assertEquals(SyncError.Code.SERVER, ex.error.code)
        assertTrue(ex.error.message!!.contains("Unknown operation"))
    }

    @Test
    fun httpUnauthorized_mapsToAuthError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"unauthenticated"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(KtorGraphQlSyncApi.defaultJson) }
        }
        val api = KtorGraphQlSyncApi(
            config = GraphQlSyncConfig(endpointUrl = "http://localhost:8080/graphql"),
            httpClient = client,
        )

        val ex = assertFailsWith<SyncTransportException> {
            api.pull(0L, emptySet(), Int.MAX_VALUE, null)
        }
        assertEquals(SyncError.Code.AUTH, ex.error.code)
    }
}