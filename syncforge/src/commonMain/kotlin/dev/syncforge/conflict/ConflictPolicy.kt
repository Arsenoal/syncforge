package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.SyncedEntity

/**
 * Maps entity types to [ConflictStrategy] instances.
 *
 * Unconfigured types use [defaultStrategy] (last-write-wins by default).
 */
class ConflictPolicy internal constructor(
    val defaultStrategy: ConflictStrategy,
    private val perEntity: Map<String, ConflictStrategy>,
) {

    fun strategyFor(entityType: String): ConflictStrategy =
        perEntity[entityType] ?: defaultStrategy

    companion object {
        val Default: ConflictPolicy = ConflictPolicy(
            defaultStrategy = ConflictStrategies.lastWriteWins(),
            perEntity = emptyMap(),
        )
    }
}

class ConflictPolicyBuilder {

    private var defaultStrategy: ConflictStrategy = ConflictStrategies.lastWriteWins()
    private val perEntity = mutableMapOf<String, ConflictStrategy>()

    fun default(strategy: ConflictStrategy) {
        defaultStrategy = strategy
    }

    fun default(kind: ConflictStrategyKind) {
        default(ConflictStrategies.fromKind(kind))
    }

    fun entity(entityType: String, block: ConflictEntityBuilder.() -> Unit) {
        val builder = ConflictEntityBuilder()
        builder.apply(block)
        perEntity[entityType] = builder.strategy
            ?: error("entity(\"$entityType\") requires a strategy — e.g. deferToUser() or merge { }")
    }

    fun build(): ConflictPolicy = ConflictPolicy(defaultStrategy, perEntity.toMap())
}

class ConflictEntityBuilder {
    internal var strategy: ConflictStrategy? = null

    fun strategy(strategy: ConflictStrategy) {
        this.strategy = strategy
    }

    fun strategy(kind: ConflictStrategyKind) {
        strategy(ConflictStrategies.fromKind(kind))
    }

    fun lastWriteWins() {
        strategy(ConflictStrategies.lastWriteWins())
    }

    fun alwaysLocal() {
        strategy(ConflictStrategies.alwaysLocal())
    }

    fun alwaysRemote() {
        strategy(ConflictStrategies.alwaysRemote())
    }

    fun deferToUser() {
        strategy(ConflictStrategies.deferToUser())
    }

    inline fun <reified T : SyncedEntity> merge(
        noinline block: MergeScope<T>.(local: T, remote: T) -> T,
    ) {
        strategy(ConflictStrategies.merge(block))
    }

    /**
     * Git-like three-way merge using [MergeBaseStore] snapshots (see [MergeBaseStore]).
     * When no merge base exists yet, [threeWayMerge] receives `base = local` (two-way fallback).
     */
    @ExperimentalSyncForgeApi
    fun <T : SyncedEntity> gitLike(
        block: GitLikeEntityBuilder<T>.() -> Unit,
    ) {
        strategy(GitLikeEntityBuilder<T>().apply(block).build())
    }
}

fun conflictPolicy(block: ConflictPolicyBuilder.() -> Unit): ConflictPolicy =
    ConflictPolicyBuilder().apply(block).build()