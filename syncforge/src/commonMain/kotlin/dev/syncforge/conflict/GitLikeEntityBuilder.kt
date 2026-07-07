package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.SyncedEntity

/**
 * DSL builder for [ConflictEntityBuilder.gitLike].
 */
@ExperimentalSyncForgeApi
class GitLikeEntityBuilder<T : SyncedEntity> {

    private var threeWayMerge: ((T, T, T) -> ThreeWayMergeResult<T>)? = null
    private var onUnmergeableStrategy: ConflictStrategy = ConflictStrategies.deferToUser()

    fun threeWayMerge(block: (base: T, local: T, remote: T) -> ThreeWayMergeResult<T>) {
        threeWayMerge = block
    }

    fun onUnmergeable(block: ConflictEntityBuilder.() -> Unit) {
        val fallback = ConflictEntityBuilder().apply(block)
        onUnmergeableStrategy = fallback.strategy ?: ConflictStrategies.deferToUser()
    }

    internal fun build(): ConflictStrategy {
        val merge = threeWayMerge
            ?: error("gitLike { } requires threeWayMerge { base, local, remote -> … }")
        return GitLikeMergeStrategy(merge, onUnmergeableStrategy)
    }
}