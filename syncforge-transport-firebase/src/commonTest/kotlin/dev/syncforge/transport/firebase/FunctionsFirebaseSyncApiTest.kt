package dev.syncforge.transport.firebase

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

class FunctionsFirebaseSyncApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun push_callsFunctionWithBearerToken() = runTest {
        var capturedUrl: String? = null
        var capturedAuth: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"acknowledgedIds":[11],"rejected":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = FunctionsFirebaseSyncApi(
            config = FirebaseSyncConfig(
                pushUrl = "https://us-central1-demo.cloudfunctions.net/syncforgePush",
                pullUrl = "https://us-central1-demo.cloudfunctions.net/syncforgePull",
                idToken = { "firebase-id-token" },
            ),
            httpClient = client,
        )

        val result = api.push(
            entries = listOf(
                OutboxEntry(
                    id = 11L,
                    entityType = "tasks",
                    entityId = "t11",
                    changeType = ChangeType.CREATE,
                    payloadJson = null,
                    localVersion = 1L,
                    createdAtMillis = 1L,
                ),
            ),
            nowMillis = 700L,
        )

        assertEquals(listOf(11L), result.acknowledgedIds)
        assertEquals("https://us-central1-demo.cloudfunctions.net/syncforgePush", capturedUrl)
        assertEquals("Bearer firebase-id-token", capturedAuth)
    }

    @Test
    fun pull_callsPullFunction() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """
                    {
                      "deltas": [],
                      "serverTimestampMillis": 1200,
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
        val api = FunctionsFirebaseSyncApi(
            config = FirebaseSyncConfig(
                pushUrl = "https://us-central1-demo.cloudfunctions.net/syncforgePush",
                pullUrl = "https://us-central1-demo.cloudfunctions.net/syncforgePull",
            ),
            httpClient = client,
        )

        val result = api.pull(
            sinceTimestampMillis = 200L,
            entityTypes = setOf("tasks"),
            pageSize = 30,
            pageCursor = null,
            nowMillis = 1_200L,
        )

        assertEquals("https://us-central1-demo.cloudfunctions.net/syncforgePull", capturedUrl)
        assertEquals(1_200L, result.serverTimestampMillis)
    }

    @Test
    fun cloudFunctions_buildsGen1Urls() {
        val config = FirebaseSyncConfig.cloudFunctions(projectId = "my-app", region = "europe-west1")
        assertEquals(
            "https://europe-west1-my-app.cloudfunctions.net/syncforgePush",
            config.pushUrl,
        )
        assertEquals(
            "https://europe-west1-my-app.cloudfunctions.net/syncforgePull",
            config.pullUrl,
        )
    }

    @Test
    fun functionFailure_mapsUnauthorizedToAuthError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"unauthenticated"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = FunctionsFirebaseSyncApi(
            config = FirebaseSyncConfig(
                pushUrl = "https://example.com/syncforgePush",
                pullUrl = "https://example.com/syncforgePull",
            ),
            httpClient = client,
        )

        val ex = assertFailsWith<SyncTransportException> {
            api.push(emptyList(), nowMillis = 0L)
        }
        assertEquals(SyncError.Code.AUTH, ex.error.code)
    }
}