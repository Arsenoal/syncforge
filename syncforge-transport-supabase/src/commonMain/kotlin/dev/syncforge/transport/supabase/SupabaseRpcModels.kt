package dev.syncforge.transport.supabase

import dev.syncforge.network.api.OutboxEntryDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SyncforgePushRpcRequest(
    val entries: List<OutboxEntryDto>,
    @SerialName("now_millis") val nowMillis: Long,
)

@Serializable
internal data class SyncforgePullRpcRequest(
    @SerialName("since_millis") val sinceMillis: Long,
    @SerialName("entity_types") val entityTypes: List<String>? = null,
    @SerialName("page_limit") val pageLimit: Int,
    @SerialName("page_cursor") val pageCursor: String? = null,
    @SerialName("now_millis") val nowMillis: Long,
)