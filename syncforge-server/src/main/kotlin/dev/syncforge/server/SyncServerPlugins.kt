package dev.syncforge.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

val syncServerJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

fun Application.installSyncServerPlugins() {
    install(ContentNegotiation) {
        json(syncServerJson)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error")),
            )
        }
    }
}