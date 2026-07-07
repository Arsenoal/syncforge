package dev.syncforge.auth

import dev.syncforge.model.SyncError
import dev.syncforge.network.SyncAuthProvider
import dev.syncforge.network.SyncTransportException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal expect fun createAuthHttpClient(engine: io.ktor.client.engine.HttpClientEngine, json: Json): HttpClient

internal expect fun createAuthHttpClient(json: Json): HttpClient

internal fun buildAuthHttpClient(
    engine: io.ktor.client.engine.HttpClientEngine,
    json: Json,
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(json)
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status.value !in 200..299) {
                val body = response.bodyAsText()
                throw SyncTransportException(authHttpStatusToSyncError(response.status, body))
            }
        }
    }
}

private fun authHttpStatusToSyncError(status: HttpStatusCode, body: String): SyncError {
    val code = when (status.value) {
        401, 403 -> SyncError.Code.AUTH
        409 -> SyncError.Code.CONFLICT
        in 400..499 -> SyncError.Code.VALIDATION
        in 500..599 -> SyncError.Code.SERVER
        else -> SyncError.Code.UNKNOWN
    }
    return SyncError(
        code = code,
        message = body.ifBlank { "HTTP ${status.value}" },
        httpStatus = status.value,
    )
}