package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

@PublishedApi
internal class GitLikeMergeStrategy<T : SyncedEntity>(
    private val threeWayMerge: (base: T, local: T, remote: T) -> ThreeWayMergeResult<T>,
    private val onUnmergeable: ConflictStrategy,
) : ConflictStrategy {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
        if (context.remote.isDeleted) {
            return ConflictOutcome.Resolved(ConflictResolution.DeleteLocal)
        }
        val remote = context.remotePayload
            ?: return ConflictOutcome.Resolved(ConflictResolution.KeepLocal(context.local))

        val typedMerge = threeWayMerge as (T, T, T) -> ThreeWayMergeResult<T>
        val base = context.mergeBasePayload ?: context.local
        return when (val result = typedMerge(base, context.local, remote)) {
            is ThreeWayMergeResult.Merged ->
                ConflictOutcome.Resolved(ConflictResolution.Merged(result.entity))

            is ThreeWayMergeResult.Unmergeable ->
                onUnmergeable.resolve(context)
        }
    }
}