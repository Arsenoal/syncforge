package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

@Suppress("DEPRECATION")
internal class LastWriteWinsStrategy(
    private val resolver: ConflictResolver = LastWriteWinsResolver(),
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