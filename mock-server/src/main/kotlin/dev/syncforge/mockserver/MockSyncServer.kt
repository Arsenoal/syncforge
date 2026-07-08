package dev.syncforge.mockserver

import dev.syncforge.server.InMemorySyncStore
import dev.syncforge.server.installSyncServerPlugins
import dev.syncforge.server.syncRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::mockSyncModule).start(wait = true)
}

fun Application.mockSyncModule() {
    val store = InMemorySyncStore()
    val e2eSessions = E2eSessionStore()
    installSyncServerPlugins()

    routing {
        syncRoutes(store)

        /**
         * Dev-only: simulates a concurrent server edit for conflict demos.
         * The entity must already exist on the server (push first).
         */
        post("/dev/reset") {
            store.clear()
            e2eSessions.resetAll()
            call.respond(mapOf("reset" to true))
        }

        post("/dev/e2e/session") {
            val request = call.receive<E2eSessionCreateRequest>()
            e2eSessions.create(request.sessionId)
            call.respond(E2eSessionCreateResponse(created = true))
        }

        post("/dev/e2e/session/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId required"))
            val request = call.receive<E2eSessionPutRequest>()
            e2eSessions.put(sessionId, request.key, request.value)
            call.respond(E2eSessionPutResponse(stored = true))
        }

        get("/dev/e2e/session/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId required"))
            } else {
                call.respond(E2eSessionSnapshotResponse(values = e2eSessions.snapshot(sessionId)))
            }
        }

        post("/dev/simulate-edit") {
            val request = call.receive<SimulateEditRequest>()
            val updatedAtMillis = store.forceUpdate(
                entityType = request.entityType,
                entityId = request.entityId,
                payloadJson = request.payloadJson,
                nowMillis = System.currentTimeMillis(),
            )
            if (updatedAtMillis != null) {
                call.respond(
                    SimulateEditResponse(
                        updated = true,
                        updatedAtMillis = updatedAtMillis,
                    ),
                )
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

        /**
         * Dev-only: tombstones an entity on the server (delete conflict demos).
         * The entity must already exist on the server (push first).
         */
        post("/dev/simulate-delete") {
            val request = call.receive<SimulateDeleteRequest>()
            val deleted = store.forceDelete(
                entityType = request.entityType,
                entityId = request.entityId,
                nowMillis = System.currentTimeMillis(),
            )
            if (deleted) {
                call.respond(SimulateDeleteResponse(deleted = true))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    SimulateDeleteResponse(
                        deleted = false,
                        message = "Entity not found — sync the task to the server first",
                    ),
                )
            }
        }
    }
}