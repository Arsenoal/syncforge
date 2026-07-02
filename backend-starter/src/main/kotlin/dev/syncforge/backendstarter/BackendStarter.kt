package dev.syncforge.backendstarter

import dev.syncforge.server.InMemorySyncStore
import dev.syncforge.server.installSyncServerPlugins
import dev.syncforge.server.syncRoutes
import dev.syncforge.server.auth.InMemoryAuthStore
import dev.syncforge.server.auth.authRoutes
import dev.syncforge.server.auth.installSyncBearerAuth
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::backendStarterModule).start(wait = true)
}

fun Application.backendStarterModule() {
    val syncStore = InMemorySyncStore()
    val authStore = InMemoryAuthStore()
    installSyncServerPlugins()
    installSyncBearerAuth(authStore)

    routing {
        authRoutes(authStore)
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        authenticate("syncforge-bearer") {
            syncRoutes(syncStore, includeHealth = false)
        }
    }
}