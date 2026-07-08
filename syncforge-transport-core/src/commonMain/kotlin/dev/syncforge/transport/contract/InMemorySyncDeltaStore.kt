package dev.syncforge.transport.contract

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.api.toDto
import dev.syncforge.network.api.toPullResult
import dev.syncforge.network.api.toPushResult
import dev.syncforge.transport.SyncDeltaStore

/** [SyncDeltaStore] backed by [InMemorySyncBackend] — reference impl for contract tests. */
@ExperimentalSyncForgeApi
class InMemorySyncDeltaStore(
    private val backend: InMemorySyncBackend = InMemorySyncBackend(),
    private val clock: () -> Long = backend::lastNowMillis,
) : SyncDeltaStore {

    override suspend fun appendEntries(entries: List<OutboxEntry>): PushResult =
        backend.push(entries.map { it.toDto() }, clock()).toPushResult()

    override suspend fun queryDeltas(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = backend.pull(
        sinceTimestampMillis = sinceTimestampMillis,
        entityTypes = entityTypes,
        nowMillis = clock(),
        pageSize = pageSize,
        pageCursor = pageCursor,
    ).toPullResult()

    fun forceDelete(entityType: String, entityId: String, nowMillis: Long): Boolean =
        backend.forceDelete(entityType, entityId, nowMillis)

    fun backend(): InMemorySyncBackend = backend
}