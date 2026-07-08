package dev.syncforge.server

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphQlRoutesTest {

    @Test
    fun syncPushThenSyncPull_roundTrip() = testApplication {
        val store = InMemorySyncStore()
        application {
            installSyncServerPlugins()
            routing {
                graphqlRoutes(store)
            }
        }

        val pushVariables = buildJsonObject {
            putJsonArray("entries") {
                add(
                    syncServerJson.encodeToJsonElement(
                        OutboxEntryDto.serializer(),
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
                )
            }
        }
        val pushBody = syncServerJson.encodeToString(
            GraphQlHttpRequest.serializer(),
            GraphQlHttpRequest(
                query = "mutation syncPush(\$entries: [OutboxEntryInput!]!) { syncPush(entries: \$entries) { acknowledgedIds } }",
                operationName = "syncPush",
                variables = pushVariables,
            ),
        )
        val pushResponse = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(pushBody)
        }
        assertEquals(HttpStatusCode.OK, pushResponse.status)
        assertTrue(pushResponse.bodyAsText().contains("acknowledgedIds"))

        val pullVariables = buildJsonObject {
            put("since", 0)
            putJsonArray("types") {}
        }
        val pullBody = syncServerJson.encodeToString(
            GraphQlHttpRequest.serializer(),
            GraphQlHttpRequest(
                query = "query syncPull(\$since: Long!, \$types: [String!]!) { syncPull(since: \$since, types: \$types) { serverTimestampMillis } }",
                operationName = "syncPull",
                variables = pullVariables,
            ),
        )
        val pullResponse = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(pullBody)
        }
        assertEquals(HttpStatusCode.OK, pullResponse.status)
        val body = pullResponse.bodyAsText()
        assertTrue(body.contains("task-42"))
        assertTrue(body.contains("Hello"))
    }
}