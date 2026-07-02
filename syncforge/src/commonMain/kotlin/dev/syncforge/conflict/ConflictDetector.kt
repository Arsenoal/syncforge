package dev.syncforge.conflict

import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState

internal object ConflictDetector {

    fun isConflict(local: SyncedEntity, remote: RemoteMetadata): Boolean =
        local.syncState == SyncState.PENDING ||
            local.syncState == SyncState.CONFLICT ||
            (local.syncState == SyncState.SYNCED && local.localVersion != remote.serverVersion)
}