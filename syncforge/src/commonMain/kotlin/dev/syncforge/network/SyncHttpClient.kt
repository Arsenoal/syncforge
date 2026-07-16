package dev.syncforge.network

import dev.syncforge.network.api.PushRequest

/**
 * Ktor-backed REST executor for SyncForge push/pull wire calls.
 *
 * Maps [PushRequest] and [PullQueryParams] to the default REST routes ([SyncHttpRoutes])
 * and returns domain [PushResult] / [PullResult]. The Ktor implementation lives in the
 * optional `syncforge-network-ktor` artifact; [RestSyncTransport] delegates here for REST.
 *
 * Non-REST backends (GraphQL, BaaS adapters) implement [SyncTransport] directly and do not
 * use this interface.
 */
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