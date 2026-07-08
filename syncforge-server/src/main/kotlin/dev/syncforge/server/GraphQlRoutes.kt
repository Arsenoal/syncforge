package dev.syncforge.server

import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.PushResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Minimal GraphQL-over-HTTP facade for SyncForge push/pull — delegates to [SyncHandlers].
 *
 * Accepts standard POST `{ query, operationName?, variables? }` and routes `syncPush` / `syncPull`
 * to the same store contract as [syncRoutes]. Full schema recipes ship in 1.4-09.
 */
fun Route.graphqlRoutes(store: SyncStore) {
    post("/graphql") {
        val request = call.receive<GraphQlHttpRequest>()
        val operation = resolveGraphQlOperation(request)
            ?: return@post call.respond(
                HttpStatusCode.OK,
                GraphQlHttpResponse<Nothing>(
                    errors = listOf(GraphQlHttpError("Unknown GraphQL operation — expected syncPush or syncPull")),
                ),
            )

        val nowMillis = System.currentTimeMillis()
        when (operation) {
            GraphQlOperation.SYNC_PUSH -> {
                val variables = decodeVariables<SyncPushGraphQlVariables>(request.variables)
                    ?: return@post call.respond(
                        HttpStatusCode.OK,
                        GraphQlHttpResponse<Nothing>(
                            errors = listOf(GraphQlHttpError("syncPush requires variables.entries")),
                        ),
                    )
                val payload = SyncHandlers.push(store, PushRequest(variables.entries), nowMillis)
                call.respond(GraphQlHttpResponse(data = SyncPushGraphQlData(payload)))
            }
            GraphQlOperation.SYNC_PULL -> {
                val variables = decodeVariables<SyncPullGraphQlVariables>(request.variables)
                    ?: return@post call.respond(
                        HttpStatusCode.OK,
                        GraphQlHttpResponse<Nothing>(
                            errors = listOf(GraphQlHttpError("syncPull requires variables.since and variables.types")),
                        ),
                    )
                val payload = SyncHandlers.pull(
                    store,
                    PullQueryParams(
                        since = variables.since,
                        types = variables.types.toSet(),
                        limit = variables.limit ?: Int.MAX_VALUE,
                        cursor = variables.cursor,
                    ),
                    nowMillis,
                )
                call.respond(GraphQlHttpResponse(data = SyncPullGraphQlData(payload)))
            }
        }
    }
}

private enum class GraphQlOperation {
    SYNC_PUSH,
    SYNC_PULL,
}

private fun resolveGraphQlOperation(request: GraphQlHttpRequest): GraphQlOperation? {
    when (request.operationName) {
        "syncPush" -> return GraphQlOperation.SYNC_PUSH
        "syncPull" -> return GraphQlOperation.SYNC_PULL
    }
    val query = request.query
    return when {
        "syncPush" in query -> GraphQlOperation.SYNC_PUSH
        "syncPull" in query -> GraphQlOperation.SYNC_PULL
        else -> null
    }
}

private inline fun <reified T> decodeVariables(variables: JsonElement?): T? =
    runCatching {
        variables?.let { syncServerJson.decodeFromJsonElement<T>(it) }
    }.getOrNull()

@Serializable
data class GraphQlHttpRequest(
    val query: String,
    val operationName: String? = null,
    val variables: JsonElement? = null,
)

@Serializable
data class GraphQlHttpError(
    val message: String,
)

@Serializable
data class GraphQlHttpResponse<T>(
    val data: T? = null,
    val errors: List<GraphQlHttpError>? = null,
)

@Serializable
internal data class SyncPushGraphQlVariables(
    val entries: List<OutboxEntryDto>,
)

@Serializable
data class SyncPushGraphQlData(
    val syncPush: PushResponse,
)

@Serializable
data class SyncPullGraphQlData(
    val syncPull: PullResponse,
)

@Serializable
internal data class SyncPullGraphQlVariables(
    val since: Long,
    val types: List<String> = emptyList(),
    val limit: Int? = null,
    val cursor: String? = null,
)