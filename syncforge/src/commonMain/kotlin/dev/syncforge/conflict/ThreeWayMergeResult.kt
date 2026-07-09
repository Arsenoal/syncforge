package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

/**
 * Result of a git-like three-way merge attempt.
 */
sealed interface ThreeWayMergeResult<out T : SyncedEntity> {

    /** Non-overlapping edits merged into a single entity to persist as [SyncState.SYNCED]. */
    data class Merged<T : SyncedEntity>(val entity: T) : ThreeWayMergeResult<T>

    /** Overlapping edits — fall back to [GitLikeEntityBuilder.onUnmergeable]. */
    data object Unmergeable : ThreeWayMergeResult<Nothing>
}