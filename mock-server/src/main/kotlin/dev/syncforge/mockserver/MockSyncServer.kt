package dev.syncforge.mockserver

import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.PullResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::mockSyncModule).start(wait = true)
}

fun Application.mockSyncModule() {
    val store = InMemorySyncStore()
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    install(ContentNegotiation) {
        json(json)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error")),
            )
        }
    }

    routing {
        post("/sync/push") {
            val request = call.receive<PushRequest>()
            val response = store.push(request.entries, System.currentTimeMillis())
            call.respond(response)
        }

        get("/sync/pull") {
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val types = call.request.queryParameters["types"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: Int.MAX_VALUE
            val cursor = call.request.queryParameters["cursor"]

            val response = store.pull(
                sinceTimestampMillis = since,
                entityTypes = types,
                nowMillis = System.currentTimeMillis(),
                limit = limit,
                pageCursor = cursor,
            )
            call.respond(response)
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        /**
         * Dev-only: simulates a concurrent server edit for conflict demos.
         * The entity must already exist on the server (push first).
         */
        post("/dev/simulate-edit") {
            val request = call.receive<SimulateEditRequest>()
            val updated = store.forceUpdate(
                entityType = request.entityType,
                entityId = request.entityId,
                payloadJson = request.payloadJson,
                nowMillis = System.currentTimeMillis(),
            )
            if (updated) {
                call.respond(SimulateEditResponse(updated = true))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    SimulateEditResponse(
                        updated = false,
                        message = "Entity not found — sync the task to the server first",
                    ),
                )
            }
        }
    }
}