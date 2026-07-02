package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

/**
 * Outcome of conflict resolution — applied to Room inside [dev.syncforge.sync.SyncEngine].
 */
sealed interface ConflictResolution<out T : SyncedEntity> {

    /** Keep the local row as-is; optionally bump sync metadata. */
    data class KeepLocal<T : SyncedEntity>(val entity: T) : ConflictResolution<T>

    /** Accept the server version wholesale. */
    data class AcceptRemote<T : SyncedEntity>(val entity: T) : ConflictResolution<T>

    /** Custom merged entity — strategy computed field-level outcome. */
    data class Merged<T : SyncedEntity>(val entity: T) : ConflictResolution<T>

    /** Server tombstone wins — delete locally. */
    data object DeleteLocal : ConflictResolution<Nothing>
}