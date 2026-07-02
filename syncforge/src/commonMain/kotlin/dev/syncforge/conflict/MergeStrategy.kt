package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

@PublishedApi
internal class MergeStrategy<T : SyncedEntity>(
    private val merge: MergeScope<T>.(local: T, remote: T) -> T,
) : ConflictStrategy {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
        if (context.remote.isDeleted) {
            return ConflictOutcome.Resolved(ConflictResolution.DeleteLocal)
        }
        val remote = context.remotePayload
            ?: return ConflictOutcome.Resolved(ConflictResolution.KeepLocal(context.local))

        val scope = MergeScope(context.local, remote, context.remote)
        val typedMerge = merge as MergeScope<T>.(T, T) -> T
        val merged = scope.typedMerge(context.local, remote)
        return ConflictOutcome.Resolved(ConflictResolution.Merged(merged))
    }
}