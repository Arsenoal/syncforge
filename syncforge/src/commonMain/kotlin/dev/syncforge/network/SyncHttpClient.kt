package dev.syncforge.network

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.network.api.PushRequest

/**
 * Ktor-backed REST executor for SyncForge push/pull wire calls.
 *
 * Maps [PushRequest] and [PullQueryParams] to the default REST routes ([SyncHttpRoutes])
 * and returns domain [PushResult] / [PullResult]. Implementations attach auth (e.g.
 * [SyncAuthProvider]) and HTTP engines; [SyncTransport] adapters such as `RestSyncTransport`
 * (1.1) delegate here instead of calling Ktor directly.
 *
 * Non-REST backends (GraphQL, BaaS adapters) implement [SyncTransport] directly and do not
 * use this interface.
 */
@ExperimentalSyncForgeApi
interface SyncHttpClient {

    suspend fun postPush(baseUrl: String, request: PushRequest): PushResult

    suspend fun getPull(baseUrl: String, params: PullQueryParams): PullResult
}

/** Query parameters for `GET /sync/pull` per the REST v1 contract. */
data class PullQueryParams(
    val sinceTimestampMillis: Long,
    val entityTypes: Set<String>,
    val pageSize: Int = Int.MAX_VALUE,
    val pageCursor: String? = null,
)