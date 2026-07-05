package dev.syncforge.debug

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.model.OutboxEntry
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Developer-facing observability API — powers the in-app Sync Debug panel.
 *
 * Access via [SyncManager.debug]. Intended for debug/QA builds (similar to Chucker).
 */
@ExperimentalSyncForgeApi
interface SyncDebug {

    /** Live health metrics derived from outbox, conflicts, network, and cursor state. */
    val health: StateFlow<SyncHealth>

    /** All outbox rows (pending, retrying, and permanently failed). */
    val outboxItems: StateFlow<List<OutboxEntry>>

    /** Ring buffer of recent sync operations and notable events. */
    val events: StateFlow<List<SyncEvent>>

    /** Full conflict records including JSON snapshots for inspection. */
    val conflictRecords: StateFlow<List<ConflictRecord>>

    /** Removes all pending outbox entries. Does not roll back optimistic Room writes. */
    suspend fun clearOutbox()

    /** Clears the in-memory event log. */
    suspend fun clearEventLog()

    /** Resets the pull cursor to 0 (persisted store + in-memory engine state). */
    suspend fun resetPullCursor()
}