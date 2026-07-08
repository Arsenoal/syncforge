package dev.syncforge.transport.contract

import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.toDto

/**
 * Spec-compliant in-memory API for contract tests — wire into vendor
 * [dev.syncforge.transport.supabase.SupabaseSyncApi] or
 * [dev.syncforge.transport.firebase.FirebaseSyncApi] fakes in test source.
 */
class ContractSyncApi(
    private val backend: InMemorySyncBackend,
) {
    suspend fun push(entries: List<OutboxEntry>, nowMillis: Long): PushResponse =
        backend.push(entries.map { it.toDto() }, nowMillis)

    suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
        nowMillis: Long,
    ): PullResponse = backend.pull(
        sinceTimestampMillis = sinceTimestampMillis,
        entityTypes = entityTypes,
        nowMillis = nowMillis,
        pageSize = pageSize,
        pageCursor = pageCursor,
    )
}