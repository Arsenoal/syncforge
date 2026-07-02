package dev.syncforge.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Route.authRoutes(store: InMemoryAuthStore) {
    post("/auth/register") {
        val fields = call.receiveAuthFields()
        val email = fields.string("email")
            ?: return@post call.respond(HttpStatusCode.BadRequest, errorBody("email is required"))
        val password = fields.string("password")
            ?: return@post call.respond(HttpStatusCode.BadRequest, errorBody("password is required"))
        val tokens = runCatching { store.register(email, password) }
            .getOrElse { return@post call.respond(HttpStatusCode.Conflict, errorBody(it.message ?: "register failed")) }
        call.respond(tokens.toResponse())
    }

    post("/auth/login") {
        val fields = call.receiveAuthFields()
        val email = fields.string("email")
            ?: return@post call.respond(HttpStatusCode.BadRequest, errorBody("email is required"))
        val password = fields.string("password")
            ?: return@post call.respond(HttpStatusCode.BadRequest, errorBody("password is required"))
        val tokens = runCatching { store.login(email, password) }
            .getOrElse { return@post call.respond(HttpStatusCode.Unauthorized, errorBody(it.message ?: "login failed")) }
        call.respond(tokens.toResponse())
    }

    post("/auth/refresh") {
        val fields = call.receiveAuthFields()
        val refresh = fields.string("refresh_token")
            ?: return@post call.respond(HttpStatusCode.BadRequest, errorBody("refresh_token is required"))
        val tokens = runCatching { store.refresh(refresh) }
            .getOrElse { return@post call.respond(HttpStatusCode.Unauthorized, errorBody(it.message ?: "refresh failed")) }
        call.respond(tokens.toResponse())
    }
}

fun Application.installSyncBearerAuth(store: InMemoryAuthStore) {
    install(Authentication) {
        bearer("syncforge-bearer") {
            authenticate { credential ->
                val email = store.validateAccessToken(credential.token)
                if (email != null) BearerPrincipal(email) else null
            }
        }
    }
}

data class BearerPrincipal(val email: String) : io.ktor.server.auth.Principal

private fun AuthTokens.toResponse(): TokenResponse = TokenResponse(
    access_token = accessToken,
    refresh_token = refreshToken,
    expires_in = expiresInSeconds,
)

@Serializable
private data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
)

private fun errorBody(message: String) = mapOf("error" to message)

private suspend fun io.ktor.server.application.ApplicationCall.receiveAuthFields(): Map<String, String> {
    val body = receive<JsonObject>()
    return body.mapValues { (_, value) -> value.jsonPrimitive.content }
}

private fun Map<String, String>.string(key: String): String? = this[key]?.takeIf { it.isNotBlank() }