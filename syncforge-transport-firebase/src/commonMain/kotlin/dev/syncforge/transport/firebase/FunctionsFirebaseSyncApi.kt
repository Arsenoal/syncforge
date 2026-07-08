package dev.syncforge.transport.firebase

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
 * HTTPS client for Firebase Cloud Functions `syncforgePush` / `syncforgePull`
 * (see `firebase/functions/`).
 */
class FunctionsFirebaseSyncApi(
    private val config: FirebaseSyncConfig,
    private val httpClient: HttpClient,
) : FirebaseSyncApi {

    override suspend fun push(entries: List<OutboxEntry>, nowMillis: Long): PushResponse =
        postFunction(
            url = config.pushUrl,
            body = SyncforgePushRequest(
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
    ): PullResponse = postFunction(
        url = config.pullUrl,
        body = SyncforgePullRequest(
            sinceMillis = sinceTimestampMillis,
            entityTypes = entityTypes.takeIf { it.isNotEmpty() }?.sorted()?.toList(),
            pageLimit = pageSize,
            pageCursor = pageCursor,
            nowMillis = nowMillis,
        ),
    )

    private suspend inline fun <reified T : Any, reified R> postFunction(
        url: String,
        body: T,
    ): R {
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            resolveIdToken()?.let { token ->
                header("Authorization", "Bearer $token")
            }
            setBody(body)
        }
        return decodeResponse(response)
    }

    private suspend fun resolveIdToken(): String? =
        config.idToken?.invoke()?.takeIf { it.isNotBlank() }

    private suspend inline fun <reified R> decodeResponse(response: HttpResponse): R {
        if (response.status.value !in 200..299) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val code = when (response.status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> SyncError.Code.AUTH
                else -> SyncError.Code.SERVER
            }
            throw SyncTransportException(
                SyncError(
                    code = code,
                    message = "Firebase function failed (${response.status.value}): $body",
                    httpStatus = response.status.value,
                ),
            )
        }
        return response.body()
    }
}