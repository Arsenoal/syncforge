package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

internal class DeferToUserStrategy : ConflictStrategy {

    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> =
        ConflictOutcome.Deferred(
            local = context.local,
            remote = context.remotePayload,
            remoteMeta = context.remote,
        )
}