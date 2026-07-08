package dev.syncforge.sample.conflicts

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictEntityBuilder
import dev.syncforge.conflict.ConflictPolicyBuilder
import dev.syncforge.conflict.ThreeWayMergeResult
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
@OptIn(ExperimentalSyncForgeApi::class)
fun ConflictPolicyBuilder.sampleEntityConflicts() {
    entity("notes") { alwaysRemote() }
    entity("tags") { lastWriteWins() }
    entity("tasks") { sampleTaskGitLikePolicy() }
}

@OptIn(ExperimentalSyncForgeApi::class)
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