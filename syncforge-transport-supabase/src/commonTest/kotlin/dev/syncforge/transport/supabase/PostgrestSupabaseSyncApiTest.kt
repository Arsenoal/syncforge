package dev.syncforge.transport.supabase

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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class PostgrestSupabaseSyncApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun push_callsSyncforgePushRpcWithSupabaseHeaders() = runTest {
        var capturedPath: String? = null
        var capturedAuth: String? = null
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"acknowledgedIds":[9],"rejected":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = PostgrestSupabaseSyncApi(
            config = SupabaseSyncConfig(
                projectUrl = "https://demo.supabase.co",
                apiKey = "anon-key",
            ),
            httpClient = client,
        )

        val result = api.push(
            entries = listOf(
                OutboxEntry(
                    id = 9L,
                    entityType = "tasks",
                    entityId = "t9",
                    changeType = ChangeType.CREATE,
                    payloadJson = null,
                    localVersion = 1L,
                    createdAtMillis = 1L,
                ),
            ),
            nowMillis = 500L,
        )

        assertEquals(listOf(9L), result.acknowledgedIds)
        assertEquals("/rest/v1/rpc/syncforge_push", capturedPath)
        assertEquals("Bearer anon-key", capturedAuth)
    }

    @Test
    fun pull_callsSyncforgePullRpc() = runTest {
        var capturedPath: String? = null
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            respond(
                content = """
                    {
                      "deltas": [],
                      "serverTimestampMillis": 900,
                      "hasMore": false,
                      "nextPageCursor": null
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = PostgrestSupabaseSyncApi(
            config = SupabaseSyncConfig(
                projectUrl = "https://demo.supabase.co",
                apiKey = "anon-key",
            ),
            httpClient = client,
        )

        val result = api.pull(
            sinceTimestampMillis = 100L,
            entityTypes = setOf("tasks", "notes"),
            pageSize = 25,
            pageCursor = "abc",
            nowMillis = 900L,
        )

        assertEquals("/rest/v1/rpc/syncforge_pull", capturedPath)
        assertEquals(900L, result.serverTimestampMillis)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun rpcFailure_mapsUnauthorizedToAuthError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"JWT expired"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = PostgrestSupabaseSyncApi(
            config = SupabaseSyncConfig(
                projectUrl = "https://demo.supabase.co",
                apiKey = "anon-key",
            ),
            httpClient = client,
        )

        val ex = assertFailsWith<SyncTransportException> {
            api.push(emptyList(), nowMillis = 0L)
        }
        assertEquals(SyncError.Code.AUTH, ex.error.code)
    }
}