package dev.syncforge.sample.conflicts

import dev.syncforge.conflict.ConflictEntityBuilder
import dev.syncforge.conflict.ConflictPolicyBuilder
import dev.syncforge.conflict.ConflictStrategyKind
import dev.syncforge.conflict.ThreeWayMergeResult
import dev.syncforge.conflict.conflictPolicy
import dev.syncforge.model.SyncState
import dev.syncforge.sample.tasks.TaskEntity

/**
 * Reference conflict wiring for [:sample] — matches the 1.2 strategy matrix in
 * [docs/ROADMAP_1_0_TO_2_0.md](../../../docs/ROADMAP_1_0_TO_2_0.md).
 *
 * | Entity | Strategy | Rationale |
 * |--------|----------|-----------|
 * | notes  | [alwaysRemote][dev.syncforge.conflict.ConflictEntityBuilder.alwaysRemote] | Server-owned content |
 * | tags   | [lastWriteWins][dev.syncforge.conflict.ConflictEntityBuilder.lastWriteWins] | Simple label rows |
 * | tasks  | [gitLike][dev.syncforge.conflict.ConflictEntityBuilder.gitLike] | Independent title/completed fields auto-merge; clashes & deletes defer |
 */
fun ConflictPolicyBuilder.sampleEntityConflicts() {
    applySampleEntityConflictKinds(SampleEntityConflictKinds.Default)
}

/** Builds a [dev.syncforge.conflict.ConflictPolicy] from persisted or UI-selected kinds (1.2-10). */
fun conflictPolicyFromSampleKinds(
    kinds: SampleEntityConflictKinds,
): dev.syncforge.conflict.ConflictPolicy = conflictPolicy {
    applySampleEntityConflictKinds(kinds)
}

private fun ConflictPolicyBuilder.applySampleEntityConflictKinds(kinds: SampleEntityConflictKinds) {
    entity("notes") { applyCatalogKind(kinds.notes) }
    entity("tags") { applyCatalogKind(kinds.tags) }
    entity("tasks") { applyCatalogKind(kinds.tasks) }
}

private fun ConflictEntityBuilder.applyCatalogKind(kind: ConflictStrategyKind) {
    when (kind) {
        ConflictStrategyKind.GIT_LIKE -> sampleTaskGitLikePolicy()
        else -> strategy(kind)
    }
}

private fun ConflictEntityBuilder.sampleTaskGitLikePolicy() {
    gitLike<TaskEntity> {
        threeWayMerge { base, local, remote ->
            val titleConflict =
                local.title != base.title && remote.title != base.title && local.title != remote.title
            val completedConflict =
                local.completed != base.completed &&
                    remote.completed != base.completed &&
                    local.completed != remote.completed
            if (titleConflict || completedConflict) {
                ThreeWayMergeResult.Unmergeable
            } else {
                ThreeWayMergeResult.Merged(
                    local.copy(
                        title = when {
                            local.title != base.title -> local.title
                            remote.title != base.title -> remote.title
                            else -> local.title
                        },
                        completed = when {
                            local.completed != base.completed -> local.completed
                            remote.completed != base.completed -> remote.completed
                            else -> local.completed
                        },
                        updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
                        syncState = SyncState.SYNCED,
                    ),
                )
            }
        }
        onUnmergeable { deferToUser() }
        onRemoteDelete { deferToUser() }
    }
}