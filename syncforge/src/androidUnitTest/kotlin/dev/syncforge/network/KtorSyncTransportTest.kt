package dev.syncforge.network

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorSyncTransportTest {

    @Test
    fun push_sendsEntriesAndParsesResponse() = runTest {
        val transport = KtorSyncTransport.createForTest(
            baseUrl = "http://localhost:8080",
            httpClient = HttpClient(
                MockEngine { request ->
                    assertEquals("/sync/push", request.url.encodedPath)
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        content = """{"acknowledgedIds":[42],"rejected":[]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(KtorSyncTransport.defaultJson)
                }
            },
        )

        val result = transport.push(
            listOf(
                OutboxEntry(
                    id = 42L,
                    entityType = "tasks",
                    entityId = "t1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"t1","title":"Test"}""",
                    localVersion = 1,
                    createdAtMillis = 100L,
                ),
            ),
        )

        assertEquals(listOf(42L), result.acknowledgedIds)
        assertTrue(result.rejected.isEmpty())
    }

    @Test
    fun pull_buildsQueryAndParsesDeltas() = runTest {
        val transport = KtorSyncTransport.createForTest(
            baseUrl = "http://localhost:8080",
            httpClient = HttpClient(
                MockEngine { request ->
                    assertEquals("/sync/pull", request.url.encodedPath)
                    assertEquals("100", request.url.parameters["since"])
                    assertEquals("tasks,notes", request.url.parameters["types"])
                    respond(
                        content = """
                            {
                              "deltas": [{
                                "entityType": "tasks",
                                "entityId": "t1",
                                "payloadJson": "{\"id\":\"t1\",\"title\":\"Remote\"}",
                                "serverVersion": 2,
                                "updatedAtMillis": 200,
                                "isDeleted": false
                              }],
                              "serverTimestampMillis": 250
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(KtorSyncTransport.defaultJson)
                }
            },
        )

        val result = transport.pull(
            sinceTimestampMillis = 100L,
            entityTypes = setOf("tasks", "notes"),
            pageSize = Int.MAX_VALUE,
            pageCursor = null,
        )

        assertEquals(1, result.deltas.size)
        assertEquals("t1", result.deltas.first().entityId)
        assertEquals(250L, result.serverTimestampMillis)
    }
}