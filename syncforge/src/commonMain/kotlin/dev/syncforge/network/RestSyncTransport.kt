package dev.syncforge.network

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.toDto

/**
 * Default REST [SyncTransport] — maps outbox entries and pull cursors to [SyncHttpClient] calls.
 *
 * Owns transport-level error wrapping and a single 401 refresh retry via [RefreshingSyncAuthProvider].
 */
@ExperimentalSyncForgeApi
class RestSyncTransport(
    baseUrl: String,
    private val httpClient: SyncHttpClient,
    private val refreshingAuth: RefreshingSyncAuthProvider? = null,
) : SyncTransport {

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        runTransportWithAuthRetry {
            httpClient.postPush(
                normalizedBaseUrl,
                PushRequest(entries = entries.map { it.toDto() }),
            )
        }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = runTransportWithAuthRetry {
        httpClient.getPull(
            normalizedBaseUrl,
            PullQueryParams(
                sinceTimestampMillis = sinceTimestampMillis,
                entityTypes = entityTypes,
                pageSize = pageSize,
                pageCursor = pageCursor,
            ),
        )
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

    private companion object {
        const val HTTP_UNAUTHORIZED: Int = 401
    }
}