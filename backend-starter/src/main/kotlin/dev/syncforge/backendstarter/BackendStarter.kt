package dev.syncforge.backendstarter

import dev.syncforge.server.InMemorySyncStore
import dev.syncforge.server.installSyncServerPlugins
import dev.syncforge.server.syncRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::backendStarterModule).start(wait = true)
}

fun Application.backendStarterModule() {
    val store = InMemorySyncStore()
    installSyncServerPlugins()
    routing {
        syncRoutes(store)
    }
}