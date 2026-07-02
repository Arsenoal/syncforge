package dev.syncforge.network

import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.toDto
import dev.syncforge.network.api.toPullResult
import dev.syncforge.network.api.toPushResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Reference [SyncTransport] using Ktor and the SyncForge REST API contract.
 *
 * Platform HTTP engines are selected via [createPlatformHttpClient].
 */
class KtorSyncTransport private constructor(
    private val baseUrl: String,
    private val httpClient: HttpClient,
) : SyncTransport {

    constructor(
        baseUrl: String,
        auth: SyncAuthProvider? = null,
    ) : this(baseUrl, createPlatformHttpClient(auth, defaultJson))

    constructor(
        baseUrl: String,
        authTokenProvider: () -> String?,
    ) : this(baseUrl, SyncAuthProvider.bearer(authTokenProvider))

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        runTransport {
            httpClient.post("$normalizedBaseUrl/sync/push") {
                contentType(ContentType.Application.Json)
                setBody(PushRequest(entries = entries.map { it.toDto() }))
            }.body<PushResponse>().toPushResult()
        }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = runTransport {
        httpClient.get("$normalizedBaseUrl/sync/pull") {
            parameter("since", sinceTimestampMillis)
            parameter("types", entityTypes.joinToString(","))
            if (pageSize != Int.MAX_VALUE) parameter("limit", pageSize)
            pageCursor?.let { parameter("cursor", it) }
        }.body<PullResponse>().toPullResult()
    }

    private inline fun <T> runTransport(block: () -> T): T =
        try {
            block()
        } catch (e: SyncTransportException) {
            throw e
        } catch (e: Exception) {
            throw SyncTransportException(
                SyncError(
                    code = SyncError.Code.NETWORK,
                    message = e.message ?: "Network request failed",
                    cause = e,
                ),
            )
        }

    companion object {
        fun createForTest(baseUrl: String, httpClient: HttpClient): KtorSyncTransport =
            KtorSyncTransport(baseUrl, httpClient)

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}

internal fun buildSyncForgeHttpClient(
    engine: io.ktor.client.engine.HttpClientEngine,
    auth: SyncAuthProvider?,
    json: Json,
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(json)
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status.value !in 200..299) {
                val body = response.bodyAsText()
                throw SyncTransportException(
                    HttpStatusMapper.toSyncError(response.status, body),
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