package dev.syncforge.transport.graphql

import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.RefreshingSyncAuthProvider
import dev.syncforge.network.SyncAuthProvider
import dev.syncforge.network.SyncTransport
import dev.syncforge.network.SyncTransportException
import io.ktor.client.HttpClient

/**
 * [SyncTransport] over GraphQL-over-HTTP — maps `syncPush` / `syncPull` operations to [PushResult] / [PullResult].
 *
 * Wire with [GraphQlSyncConfig] pointing at your GraphQL endpoint (e.g. `:mock-server` `/graphql` facade).
 */
class GraphQlSyncTransport private constructor(
    private val api: GraphQlSyncApi,
    private val refreshingAuth: RefreshingSyncAuthProvider?,
) : SyncTransport {

    constructor(
        config: GraphQlSyncConfig,
        httpClient: HttpClient,
        auth: RefreshingSyncAuthProvider? = null,
    ) : this(
        api = KtorGraphQlSyncApi(config, httpClient),
        refreshingAuth = auth,
    )

    constructor(
        config: GraphQlSyncConfig,
        httpClient: HttpClient,
        auth: SyncAuthProvider?,
    ) : this(
        api = KtorGraphQlSyncApi(
            config = if (config.bearerToken == null && auth != null) {
                config.copy(bearerToken = { auth.bearerToken() })
            } else {
                config
            },
            httpClient = httpClient,
        ),
        refreshingAuth = auth as? RefreshingSyncAuthProvider,
    )

    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        runTransportWithAuthRetry { api.push(entries) }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = runTransportWithAuthRetry {
        api.pull(sinceTimestampMillis, entityTypes, pageSize, pageCursor)
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
                    message = e.message ?: "GraphQL request failed",
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
        fun createForTest(
            api: GraphQlSyncApi,
            refreshingAuth: RefreshingSyncAuthProvider? = null,
        ): GraphQlSyncTransport = GraphQlSyncTransport(api, refreshingAuth)

        fun createForTest(
            config: GraphQlSyncConfig,
            httpClient: HttpClient,
            refreshingAuth: RefreshingSyncAuthProvider? = null,
        ): GraphQlSyncTransport = GraphQlSyncTransport(config, httpClient, refreshingAuth)

        private const val HTTP_UNAUTHORIZED: Int = 401
    }
}