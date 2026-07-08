package dev.syncforge.transport

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult

/**
 * Client-side storage/query port for BaaS and custom backends (1.4).
 *
 * Mirrors server [dev.syncforge.server.SyncStore] semantics: append outbox batches on push,
 * query deltas since a timestamp on pull. Implement once per backend (Firestore, Supabase RPC,
 * JDBC, etc.); wire via [DeltaStoreSyncTransport].
 *
 * Push/pull field shapes match `docs/REST_API.md` — acknowledged/rejected ids, paginated pull
 * cursors, opaque `payloadJson` per delta.
 */
@ExperimentalSyncForgeApi
interface SyncDeltaStore {

    /**
     * Persist a batch of local outbox entries to the remote delta log.
     *
     * @return acknowledged outbox ids and per-entry rejections (conflict, validation, etc.)
     */
    suspend fun appendEntries(entries: List<OutboxEntry>): PushResult

    /**
     * Query remote deltas with [updatedAtMillis] strictly greater than [sinceTimestampMillis].
     *
     * @param entityTypes when empty, no entity-type filter (all types)
     * @param pageSize max deltas in this page; use [Int.MAX_VALUE] for no limit
     * @param pageCursor opaque cursor from a previous page's [PullResult.nextPageCursor]
     */
    suspend fun queryDeltas(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult
}