package dev.syncforge.transport.graphql

import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class GraphQlRequest(
    val query: String,
    val operationName: String? = null,
    val variables: JsonElement? = null,
)

@Serializable
internal data class GraphQlError(
    val message: String,
)

@Serializable
internal data class GraphQlResponse<T>(
    val data: T? = null,
    val errors: List<GraphQlError>? = null,
)

@Serializable
internal data class SyncPushMutationData(
    val syncPush: PushResponse,
)

@Serializable
internal data class SyncPullQueryData(
    val syncPull: PullResponse,
)

@Serializable
internal data class SyncPushVariables(
    val entries: List<dev.syncforge.network.api.OutboxEntryDto>,
)

@Serializable
internal data class SyncPullVariables(
    val since: Long,
    val types: List<String>,
    val limit: Int? = null,
    val cursor: String? = null,
)