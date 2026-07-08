package dev.syncforge.network

import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.toPullResult
import dev.syncforge.network.api.toPushResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Ktor JS [SyncHttpClient] for browser push/pull — used by [createWebKtorSyncTransport].
 */
internal class JsKtorSyncHttpClient(
    private val httpClient: HttpClient,
) : SyncHttpClient {

    override suspend fun postPush(baseUrl: String, request: PushRequest): PushResult =
        httpClient.post("${baseUrl.trimEnd('/')}${SyncHttpRoutes.PUSH_PATH}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<PushResponse>().toPushResult()

    override suspend fun getPull(baseUrl: String, params: PullQueryParams): PullResult =
        httpClient.get("${baseUrl.trimEnd('/')}${SyncHttpRoutes.PULL_PATH}") {
            parameter("since", params.sinceTimestampMillis)
            parameter("types", params.entityTypes.joinToString(","))
            if (params.pageSize != Int.MAX_VALUE) {
                parameter("limit", params.pageSize)
            }
            params.pageCursor?.let { parameter("cursor", it) }
        }.body<PullResponse>().toPullResult()
}