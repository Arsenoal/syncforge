package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

internal class AlwaysLocalStrategy : ConflictStrategy {

    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> =
        ConflictOutcome.Resolved(ConflictResolution.KeepLocal(context.local))
}