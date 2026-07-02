package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

internal class AlwaysRemoteStrategy : ConflictStrategy {

    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
        if (context.remote.isDeleted) {
            return ConflictOutcome.Resolved(ConflictResolution.DeleteLocal)
        }
        val remote = context.remotePayload
            ?: return ConflictOutcome.Resolved(ConflictResolution.KeepLocal(context.local))
        return ConflictOutcome.Resolved(ConflictResolution.AcceptRemote(remote))
    }
}