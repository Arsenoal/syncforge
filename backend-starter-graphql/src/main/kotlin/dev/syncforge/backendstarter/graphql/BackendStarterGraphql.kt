package dev.syncforge.backendstarter.graphql

import dev.syncforge.server.InMemorySyncStore
import dev.syncforge.server.graphqlRoutes
import dev.syncforge.server.installSyncServerPlugins
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::backendStarterGraphqlModule).start(wait = true)
}

fun Application.backendStarterGraphqlModule() {
    val store = InMemorySyncStore()
    installSyncServerPlugins()

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        graphqlRoutes(store)
    }
}