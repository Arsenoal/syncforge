package dev.syncforge.transport.supabase

import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.SyncTransportException
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.toDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * PostgREST RPC client for `syncforge_push` / `syncforge_pull` (see `supabase/migrations/`).
 */
class PostgrestSupabaseSyncApi(
    private val config: SupabaseSyncConfig,
    private val httpClient: HttpClient,
) : SupabaseSyncApi {

    override suspend fun push(entries: List<OutboxEntry>, nowMillis: Long): PushResponse =
        postRpc(
            rpcName = config.pushRpcName,
            body = SyncforgePushRpcRequest(
                entries = entries.map { it.toDto() },
                nowMillis = nowMillis,
            ),
        )

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
        nowMillis: Long,
    ): PullResponse = postRpc(
        rpcName = config.pullRpcName,
        body = SyncforgePullRpcRequest(
            sinceMillis = sinceTimestampMillis,
            entityTypes = entityTypes.takeIf { it.isNotEmpty() }?.sorted()?.toList(),
            pageLimit = pageSize,
            pageCursor = pageCursor,
            nowMillis = nowMillis,
        ),
    )

    private suspend inline fun <reified T : Any, reified R> postRpc(
        rpcName: String,
        body: T,
    ): R {
        val response = httpClient.post("${config.restBaseUrl}/rpc/$rpcName") {
            contentType(ContentType.Application.Json)
            header("apikey", config.apiKey)
            header("Authorization", "Bearer ${resolveBearerToken()}")
            setBody(body)
        }
        return decodeRpcResponse(response)
    }

    private suspend fun resolveBearerToken(): String =
        config.accessToken?.invoke()?.takeIf { it.isNotBlank() } ?: config.apiKey

    private suspend inline fun <reified R> decodeRpcResponse(response: HttpResponse): R {
        if (response.status.value !in 200..299) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val code = when (response.status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> SyncError.Code.AUTH
                else -> SyncError.Code.SERVER
            }
            throw SyncTransportException(
                SyncError(
                    code = code,
                    message = "Supabase RPC failed (${response.status.value}): $body",
                    httpStatus = response.status.value,
                ),
            )
        }
        return response.body()
    }
}