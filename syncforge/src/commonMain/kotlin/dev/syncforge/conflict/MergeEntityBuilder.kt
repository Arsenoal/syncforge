package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

/**
 * DSL builder for [ConflictEntityBuilder.merge] with optional [onRemoteDelete].
 */
class MergeEntityBuilder<T : SyncedEntity> {

    private var mergeBlock: (MergeScope<T>.(local: T, remote: T) -> T)? = null
    private var onRemoteDeleteStrategy: ConflictStrategy = DeleteLocalOnRemoteTombstoneStrategy

    fun merge(block: MergeScope<T>.(local: T, remote: T) -> T) {
        mergeBlock = block
    }

    fun onRemoteDelete(block: ConflictEntityBuilder.() -> Unit) {
        onRemoteDeleteStrategy = ConflictEntityBuilder().strategyForRemoteDelete(block)
    }

    internal fun build(): ConflictStrategy {
        val merge = mergeBlock
            ?: error("merge { } requires merge(local, remote) { … }")
        return MergeStrategy(merge, onRemoteDeleteStrategy)
    }
}