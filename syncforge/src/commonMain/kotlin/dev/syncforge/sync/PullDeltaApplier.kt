package dev.syncforge.sync

import dev.syncforge.conflict.ConflictPullApplier
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.network.RemoteDelta

internal class PullDeltaApplier(
    private val registry: EntityRegistry,
    private val conflictApplier: ConflictPullApplier,
) {

    suspend fun applyAll(deltas: List<RemoteDelta>): PullStats {
        var conflictsResolved = 0
        var deleted = 0
        var applied = 0

        for (delta in deltas) {
            val handler = registry.requireHandler(delta.entityType)
            val outcome = when (handler) {
                is TypedEntitySyncHandler<*> ->
                    conflictApplier.applyDelta(handler, delta)

                else -> handler.applyPullDelta(delta)
            }
            when (outcome) {
                PullApplyOutcome.DELETED -> deleted++
                PullApplyOutcome.CONFLICT_RESOLVED -> conflictsResolved++
                PullApplyOutcome.INSERTED, PullApplyOutcome.UPDATED -> applied++
                PullApplyOutcome.SKIPPED -> Unit
            }
        }

        return PullStats(
            applied = applied,
            conflictsResolved = conflictsResolved,
            deleted = deleted,
        )
    }
}

internal data class PullStats(
    val applied: Int,
    val conflictsResolved: Int,
    val deleted: Int,
)