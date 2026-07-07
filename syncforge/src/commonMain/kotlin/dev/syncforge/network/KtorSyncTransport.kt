package dev.syncforge.network

import dev.syncforge.model.OutboxEntry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Reference [SyncTransport] using Ktor and the SyncForge REST API contract.
 *
 * Delegates to [RestSyncTransport] over [KtorSyncHttpClient]. Platform HTTP engines are
 * selected via [createPlatformHttpClient]. Pass [RefreshingSyncAuthProvider] to retry once
 * after HTTP 401 following [RefreshingSyncAuthProvider.refreshAccessToken].
 */
class KtorSyncTransport private constructor(
    private val delegate: RestSyncTransport,
) : SyncTransport {

    constructor(
        baseUrl: String,
        auth: SyncAuthProvider? = null,
    ) : this(
        RestSyncTransport(
            baseUrl = baseUrl,
            httpClient = KtorSyncHttpClient(createPlatformHttpClient(auth, defaultJson)),
            refreshingAuth = auth as? RefreshingSyncAuthProvider,
        ),
    )

    constructor(
        baseUrl: String,
        authTokenProvider: () -> String?,
    ) : this(baseUrl, SyncAuthProvider.bearer(authTokenProvider))

    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        delegate.push(entries)

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = delegate.pull(sinceTimestampMillis, entityTypes, pageSize, pageCursor)

    companion object {
        fun createForTest(
            baseUrl: String,
            httpClient: HttpClient,
            refreshingAuth: RefreshingSyncAuthProvider? = null,
        ): KtorSyncTransport = KtorSyncTransport(
            RestSyncTransport(
                baseUrl = baseUrl,
                httpClient = KtorSyncHttpClient(httpClient),
                refreshingAuth = refreshingAuth,
            ),
        )

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