package dev.syncforge.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val SyncForgeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

fun buildSyncForgeHttpClient(
    engine: io.ktor.client.engine.HttpClientEngine,
    auth: SyncAuthProvider?,
    json: Json = SyncForgeJson,
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(json)
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status.value !in 200..299) {
                val body = response.bodyAsText()
                throw SyncTransportException(
                    HttpStatusMapper.toSyncError(
                        status = response.status,
                        body = body,
                        retryAfterHeader = response.headers[HttpHeaders.RetryAfter],
                    ),
                )
            }
        }
    }
    auth?.let { provider ->
        defaultRequest {
            provider.bearerToken()?.let { token ->
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}