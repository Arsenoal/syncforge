package dev.syncforge.entity

import dev.syncforge.conflict.ConflictPolicy
import dev.syncforge.conflict.ConflictPullApplier
import dev.syncforge.conflict.NoOpConflictStore
import dev.syncforge.model.Change
import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncState
import dev.syncforge.network.RemoteDelta

/**
 * Convenience base class — implement typed DAO operations; JSON wiring is handled here.
 *
 * Conflict resolution is configured via [SyncForge.android] `conflicts { }` block.
 */
abstract class TypedEntitySyncHandler<T : SyncedEntity> : EntitySyncHandler {

    protected abstract fun toJson(entity: T): String
    protected abstract fun fromJson(json: String): T
    protected abstract suspend fun findById(id: String): T?
    protected abstract suspend fun insert(entity: T)
    protected abstract suspend fun update(entity: T)
    protected abstract suspend fun deleteById(id: String)
    abstract fun withSyncState(entity: T, state: SyncState): T

    internal suspend fun findLocal(id: String): T? = findById(id)

    fun decodePayload(json: String): T = fromJson(json)

    internal fun encodePayload(entity: T): String = toJson(entity)

    internal fun encodePayloadForMergeBase(entity: SyncedEntity): String {
        @Suppress("UNCHECKED_CAST")
        return encodePayload(entity as T)
    }

    internal suspend fun persistEntity(entity: T, insert: Boolean) {
        if (insert) insert(entity) else update(entity)
    }

    internal suspend fun deleteLocal(id: String) {
        deleteById(id)
    }

    override suspend fun captureSnapshot(entityId: String): String? =
        findById(entityId)?.let { toJson(it) }

    @Suppress("UNCHECKED_CAST")
    override suspend fun applyOptimistic(change: Change<*>) {
        val typed = change as Change<T>
        when (typed.type) {
            ChangeType.CREATE, ChangeType.UPDATE -> {
                val entity = requireNotNull(typed.payload) { "CREATE/UPDATE requires payload" }
                val pending = withSyncState(entity, SyncState.PENDING)
                if (typed.type == ChangeType.CREATE) insert(pending) else update(pending)
            }
            ChangeType.DELETE -> {
                deleteById(typed.entityId)
            }
        }
    }

    override suspend fun rollbackEntry(entry: OutboxEntry) {
        when {
            entry.rollbackSnapshotJson == null -> deleteById(entry.entityId)
            else -> {
                val restored = fromJson(entry.rollbackSnapshotJson)
                val synced = withSyncState(restored, SyncState.SYNCED)
                if (findById(entry.entityId) == null) insert(synced) else update(synced)
            }
        }
    }

    override suspend fun onPushAcknowledged(entryEntityId: String) {
        val current = findById(entryEntityId) ?: return
        update(withSyncState(current, SyncState.SYNCED))
    }

    override suspend fun applyPullDelta(delta: RemoteDelta): PullApplyOutcome {
        val applier = ConflictPullApplier(
            policy = ConflictPolicy.Default,
            conflictStore = NoOpConflictStore,
        )
        return applier.applyDelta(this, delta)
    }

    @Suppress("UNCHECKED_CAST")
    override fun serializeChange(change: Change<*>): String? {
        if (change.type == ChangeType.DELETE) return null
        val payload = (change as Change<T>).payload ?: return null
        return toJson(payload)
    }
}