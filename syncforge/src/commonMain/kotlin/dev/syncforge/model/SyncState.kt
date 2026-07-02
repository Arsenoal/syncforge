package dev.syncforge.model

/**
 * Local sync state for a [dev.syncforge.entity.SyncedEntity].
 *
 * The UI reads this from Room — never from the network layer.
 */
enum class SyncState {
    /** Matches the last known server state. */
    SYNCED,

    /** Local change queued in the outbox, not yet acknowledged by the server. */
    PENDING,

    /** Server and local versions diverged; awaiting user resolution or policy merge. */
    CONFLICT,

    /** Push failed after retries; user or developer action may be required. */
    FAILED,
}