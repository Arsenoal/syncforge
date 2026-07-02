package dev.syncforge.conflict

import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity

/**
 * Helpers for field-level merge functions registered via [ConflictStrategies.merge].
 */
class MergeScope<T : SyncedEntity>(
    val local: T,
    val remote: T,
    val remoteMeta: RemoteMetadata,
) {
    fun <V> preferNewer(localValue: V, remoteValue: V): V =
        if (remoteMeta.updatedAtMillis >= local.updatedAtMillis) remoteValue else localValue

    fun <V> preferLocal(localValue: V, remoteValue: V): V = localValue

    fun <V> preferRemote(localValue: V, remoteValue: V): V = remoteValue
}