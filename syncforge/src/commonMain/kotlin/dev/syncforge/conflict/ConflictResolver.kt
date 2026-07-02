package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity

/**
 * Resolves divergent local and remote entity states during pull/push.
 *
 * Plug in custom strategies (field-level merge, CRDT, user-prompt) by implementing this interface.
 */
@Deprecated(
    message = "Use ConflictPolicy and ConflictStrategies instead. Will be removed in 1.0.",
    replaceWith = ReplaceWith(
        expression = "ConflictPolicy.Default",
        imports = ["dev.syncforge.conflict.ConflictPolicy"],
    ),
)
@ExperimentalSyncForgeApi
interface ConflictResolver {

    /**
     * @param local current row in Room (may reflect an optimistic update).
     * @param remote metadata + payload from the server delta.
     * @param remotePayload deserialized server entity, or `null` if tombstoned.
     * @return [ConflictResolution] describing how to update the local row.
     */
    fun <T : SyncedEntity> resolve(
        local: T,
        remote: RemoteMetadata,
        remotePayload: T?,
    ): ConflictResolution<T>
}

/**
 * Outcome of conflict resolution — applied to Room inside [dev.syncforge.sync.SyncEngine].
 */
sealed interface ConflictResolution<out T : SyncedEntity> {

    /** Keep the local row as-is; optionally bump sync metadata. */
    data class KeepLocal<T : SyncedEntity>(val entity: T) : ConflictResolution<T>

    /** Accept the server version wholesale. */
    data class AcceptRemote<T : SyncedEntity>(val entity: T) : ConflictResolution<T>

    /** Custom merged entity — resolver computed field-level outcome. */
    data class Merged<T : SyncedEntity>(val entity: T) : ConflictResolution<T>

    /** Server tombstone wins — delete locally. */
    data object DeleteLocal : ConflictResolution<Nothing>
}