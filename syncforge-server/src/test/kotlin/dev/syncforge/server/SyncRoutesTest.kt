package dev.syncforge.server

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PushRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRoutesTest {

    @Test
    fun pushThenPull_roundTrip() = testApplication {
        val store = InMemorySyncStore()
        application {
            installSyncServerPlugins()
            routing {
                syncRoutes(store)
            }
        }

        val pushBody = syncServerJson.encodeToString(
            PushRequest.serializer(),
            PushRequest(
                entries = listOf(
                    OutboxEntryDto(
                        id = 42,
                        entityType = "tasks",
                        entityId = "task-42",
                        changeType = ChangeType.CREATE,
                        payloadJson = """{"title":"Hello"}""",
                        localVersion = 1,
                        createdAtMillis = 1,
                    ),
                ),
            ),
        )
        val pushResponse = client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(pushBody)
        }
        assertEquals(HttpStatusCode.OK, pushResponse.status)

        val pullResponse = client.get("/sync/pull?since=0")
        assertEquals(HttpStatusCode.OK, pullResponse.status)
        val body = pullResponse.bodyAsText()
        assertTrue(body.contains("task-42"))
        assertTrue(body.contains("Hello"))
    }
}