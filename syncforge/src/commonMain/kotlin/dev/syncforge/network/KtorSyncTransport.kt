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
 * Pass [RefreshingSyncAuthProvider] to retry once after HTTP 401 following [RefreshingSyncAuthProvider.refreshAccessToken].
 */
class KtorSyncTransport private constructor(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val refreshingAuth: RefreshingSyncAuthProvider?,
) : SyncTransport {

    constructor(
        baseUrl: String,
        auth: SyncAuthProvider? = null,
    ) : this(
        baseUrl = baseUrl,
        httpClient = createPlatformHttpClient(auth, defaultJson),
        refreshingAuth = auth as? RefreshingSyncAuthProvider,
    )

    constructor(
        baseUrl: String,
        authTokenProvider: () -> String?,
    ) : this(baseUrl, SyncAuthProvider.bearer(authTokenProvider))

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        runTransportWithAuthRetry {
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
    ): PullResult = runTransportWithAuthRetry {
        httpClient.get("$normalizedBaseUrl/sync/pull") {
            parameter("since", sinceTimestampMillis)
            parameter("types", entityTypes.joinToString(","))
            if (pageSize != Int.MAX_VALUE) parameter("limit", pageSize)
            pageCursor?.let { parameter("cursor", it) }
        }.body<PullResponse>().toPullResult()
    }

    private suspend inline fun <T> runTransport(block: suspend () -> T): T =
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

    private suspend inline fun <T> runTransportWithAuthRetry(block: suspend () -> T): T {
        return try {
            runTransport(block)
        } catch (e: SyncTransportException) {
            val auth = refreshingAuth
            if (
                auth != null &&
                e.error.code == SyncError.Code.AUTH &&
                e.error.httpStatus == HTTP_UNAUTHORIZED
            ) {
                val refreshed = auth.refreshAccessToken()
                if (!refreshed.isNullOrBlank()) {
                    return runTransport(block)
                }
            }
            throw e
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED: Int = 401

        fun createForTest(
            baseUrl: String,
            httpClient: HttpClient,
            refreshingAuth: RefreshingSyncAuthProvider? = null,
        ): KtorSyncTransport = KtorSyncTransport(baseUrl, httpClient, refreshingAuth)

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