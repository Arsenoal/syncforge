package dev.syncforge.model

/**
 * Observable sync lifecycle state for UI layers (Compose, ViewModel, etc.).
 *
 * Exposed as a [kotlinx.coroutines.flow.StateFlow] from [dev.syncforge.sync.SyncManager].
 */
sealed interface SyncStatus {

    /** No sync in progress; may still have pending outbox entries. */
    data object Idle : SyncStatus

    /** A push, pull, or full sync cycle is running. */
    data class Syncing(val phase: Phase = Phase.FULL) : SyncStatus {
        enum class Phase { PUSH, PULL, FULL }
    }

    /** Local changes waiting to be pushed. */
    data class Pending(
        val outboxCount: Int,
        val permanentlyFailedCount: Int = 0,
        val conflictCount: Int = 0,
    ) : SyncStatus

    /** Device has no network — outbox entries are queued but not syncing. */
    data class Offline(val outboxCount: Int) : SyncStatus

    /** Last successful sync timestamp (epoch millis). */
    data class LastSynced(val timestampMillis: Long) : SyncStatus

    /** Unrecoverable or user-visible sync error. */
    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : SyncStatus
}