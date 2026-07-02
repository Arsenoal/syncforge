package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

sealed interface ConflictChoice {

    data object KeepLocal : ConflictChoice

    data object AcceptRemote : ConflictChoice

    data class Custom<T : SyncedEntity>(val merged: T) : ConflictChoice
}

internal fun ConflictChoice.toResolution(
    localJson: String,
    remoteJson: String?,
    deserialize: (String) -> SyncedEntity,
): ConflictResolution<SyncedEntity> = when (this) {
    ConflictChoice.KeepLocal ->
        ConflictResolution.KeepLocal(deserialize(localJson))

    ConflictChoice.AcceptRemote -> {
        val remote = remoteJson
            ?: return ConflictResolution.KeepLocal(deserialize(localJson))
        ConflictResolution.AcceptRemote(deserialize(remote))
    }

    is ConflictChoice.Custom<*> ->
        ConflictResolution.Merged(merged)
}