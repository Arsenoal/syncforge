package dev.syncforge.transport.graphql

import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.SyncTransportException
import dev.syncforge.network.api.toDto
import dev.syncforge.network.api.toPullResult
import dev.syncforge.network.api.toPushResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Ktor-backed [GraphQlSyncApi] — POST `{ query, variables }` to a GraphQL endpoint.
 */
class KtorGraphQlSyncApi(
    private val config: GraphQlSyncConfig,
    private val httpClient: HttpClient,
    private val json: Json = defaultJson,
) : GraphQlSyncApi {

    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        execute(
            operationName = "syncPush",
            query = GraphQlOperations.SYNC_PUSH,
            variables = SyncPushVariables(entries = entries.map { it.toDto() }),
            dataSerializer = SyncPushMutationData.serializer(),
        ) { it.syncPush.toPushResult() }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = execute(
        operationName = "syncPull",
        query = GraphQlOperations.SYNC_PULL,
        variables = SyncPullVariables(
            since = sinceTimestampMillis,
            types = entityTypes.sorted(),
            limit = pageSize.takeIf { it != Int.MAX_VALUE },
            cursor = pageCursor,
        ),
        dataSerializer = SyncPullQueryData.serializer(),
    ) { it.syncPull.toPullResult() }

    private suspend inline fun <reified V, reified T, R> execute(
        operationName: String,
        query: String,
        variables: V,
        dataSerializer: kotlinx.serialization.KSerializer<T>,
        crossinline mapData: (T) -> R,
    ): R {
        val response = httpClient.post(config.endpointUrl) {
            contentType(ContentType.Application.Json)
            config.bearerToken?.invoke()?.takeIf { it.isNotBlank() }?.let { token ->
                header("Authorization", "Bearer $token")
            }
            setBody(
                GraphQlRequest(
                    query = query,
                    operationName = operationName,
                    variables = json.encodeToJsonElement(variables),
                ),
            )
        }
        return decodeResponse(response, dataSerializer, mapData)
    }

    private suspend inline fun <reified T, R> decodeResponse(
        response: HttpResponse,
        dataSerializer: kotlinx.serialization.KSerializer<T>,
        crossinline mapData: (T) -> R,
    ): R {
        if (response.status.value !in 200..299) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val code = when (response.status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> SyncError.Code.AUTH
                else -> SyncError.Code.SERVER
            }
            throw SyncTransportException(
                SyncError(
                    code = code,
                    message = "GraphQL request failed (${response.status.value}): $body",
                    httpStatus = response.status.value,
                ),
            )
        }

        val envelope = json.decodeFromString(
            GraphQlResponse.serializer(dataSerializer),
            response.bodyAsText(),
        )
        envelope.errors?.firstOrNull()?.let { error ->
            throw SyncTransportException(
                SyncError(
                    code = SyncError.Code.SERVER,
                    message = "GraphQL error: ${error.message}",
                ),
            )
        }
        val data = envelope.data
            ?: throw SyncTransportException(
                SyncError(
                    code = SyncError.Code.SERVER,
                    message = "GraphQL response missing data",
                ),
            )
        return mapData(data)
    }

    companion object {
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}