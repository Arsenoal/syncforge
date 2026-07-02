package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

internal class LegacyResolverStrategy(
    private val resolver: ConflictResolver,
) : ConflictStrategy {

    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
        val resolution = resolver.resolve(
            local = context.local,
            remote = context.remote,
            remotePayload = context.remotePayload,
        )
        return ConflictOutcome.Resolved(resolution)
    }
}