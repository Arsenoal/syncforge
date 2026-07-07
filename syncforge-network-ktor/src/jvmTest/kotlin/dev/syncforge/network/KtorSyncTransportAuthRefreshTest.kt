package dev.syncforge.network

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorSyncTransportAuthRefreshTest {

    @Test
    fun push_on401_refreshesTokenAndRetriesOnce() = runTest {
        var accessToken = "stale-token"
        var refreshCalls = 0
        var pushCalls = 0

        val auth = RefreshingSyncAuthProvider(
            accessTokenProvider = { accessToken },
            refresh = {
                refreshCalls++
                accessToken = "fresh-token"
                accessToken
            },
        )

        val transport = transportWithAuth(
            auth = auth,
            engine = MockEngine { request ->
                pushCalls++
                val bearer = request.headers[HttpHeaders.Authorization]
                when {
                    pushCalls == 1 -> {
                        assertEquals("Bearer stale-token", bearer)
                        respond(
                            content = "Unauthorized",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders(),
                        )
                    }
                    else -> {
                        assertEquals("Bearer fresh-token", bearer)
                        respond(
                            content = """{"acknowledgedIds":[1],"rejected":[]}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders(),
                        )
                    }
                }
            },
        )

        val result = transport.push(listOf(sampleEntry()))

        assertEquals(listOf(1L), result.acknowledgedIds)
        assertEquals(1, refreshCalls)
        assertEquals(2, pushCalls)
    }

    @Test
    fun push_on401_whenRefreshFails_doesNotRetry() = runTest {
        var pushCalls = 0

        val auth = RefreshingSyncAuthProvider(
            accessTokenProvider = { "stale-token" },
            refresh = { null },
        )

        val transport = transportWithAuth(
            auth = auth,
            engine = MockEngine {
                pushCalls++
                respond(
                    content = "Unauthorized",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders(),
                )
            },
        )

        val error = assertFailsWith<SyncTransportException> {
            transport.push(listOf(sampleEntry()))
        }

        assertEquals(SyncError.Code.AUTH, error.error.code)
        assertEquals(401, error.error.httpStatus)
        assertEquals(1, pushCalls)
    }

    @Test
    fun push_on403_doesNotRefresh() = runTest {
        var refreshCalls = 0
        var pushCalls = 0

        val auth = RefreshingSyncAuthProvider(
            accessTokenProvider = { "token" },
            refresh = {
                refreshCalls++
                "new-token"
            },
        )

        val transport = transportWithAuth(
            auth = auth,
            engine = MockEngine {
                pushCalls++
                respond(
                    content = "Forbidden",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders(),
                )
            },
        )

        assertFailsWith<SyncTransportException> {
            transport.push(listOf(sampleEntry()))
        }

        assertEquals(0, refreshCalls)
        assertEquals(1, pushCalls)
    }

    private fun transportWithAuth(
        auth: RefreshingSyncAuthProvider,
        engine: MockEngine,
    ): KtorSyncTransport {
        val client = buildSyncForgeHttpClient(engine, auth, KtorSyncTransport.defaultJson)
        return KtorSyncTransport.createForTest(
            baseUrl = "http://localhost:8080",
            httpClient = client,
            refreshingAuth = auth,
        )
    }

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun sampleEntry() = OutboxEntry(
        id = 1L,
        entityType = "tasks",
        entityId = "t1",
        changeType = ChangeType.CREATE,
        payloadJson = """{"id":"t1","title":"Test"}""",
        localVersion = 1,
        createdAtMillis = 100L,
    )
}