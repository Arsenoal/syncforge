package dev.syncforge.entity

import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.RemoteDelta

/**
 * Per-entity-type bridge between SyncForge and your Room DAOs.
 *
 * Register one handler per synced entity type via [EntityRegistry].
 */
interface EntitySyncHandler {
    val entityType: String

    /** JSON snapshot of the row before an optimistic write; `null` if the row did not exist. */
    suspend fun captureSnapshot(entityId: String): String?

    /** Apply a local change to Room immediately (optimistic update). */
    suspend fun applyOptimistic(change: Change<*>)

    /** Restore Room to the outbox snapshot after a failed push. */
    suspend fun rollbackEntry(entry: OutboxEntry)

    /** Mark the entity [SyncState.SYNCED] after the server acknowledges the push. */
    suspend fun onPushAcknowledged(entryEntityId: String)

    /** Apply a server delta, running conflict resolution when a local row exists. */
    suspend fun applyPullDelta(delta: RemoteDelta): PullApplyOutcome

    /** Serialize CREATE/UPDATE payload for the outbox. Returns `null` for DELETE. */
    fun serializeChange(change: Change<*>): String?
}

enum class PullApplyOutcome {
    INSERTED,
    UPDATED,
    DELETED,
    CONFLICT_RESOLVED,
    SKIPPED,
}