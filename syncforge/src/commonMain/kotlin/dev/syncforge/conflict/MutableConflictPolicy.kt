package dev.syncforge.conflict

import kotlin.concurrent.Volatile

/**
 * Thread-safe holder for a [ConflictPolicy] that [SyncManager.updateConflictPolicy] can replace at runtime.
 */
internal class MutableConflictPolicy(
    initial: ConflictPolicy = ConflictPolicy.Default,
) {
    @Volatile
    private var policy: ConflictPolicy = initial

    fun snapshot(): ConflictPolicy = policy

    fun update(newPolicy: ConflictPolicy) {
        policy = newPolicy
    }

    fun strategyFor(entityType: String): ConflictStrategy = policy.strategyFor(entityType)
}