package dev.syncforge.sync

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.auth.AuthResult
import dev.syncforge.auth.AuthState
import dev.syncforge.auth.Session
import dev.syncforge.conflict.ConflictChoice
import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.conflict.ConflictSummary
import dev.syncforge.debug.SyncDebug
import dev.syncforge.model.Change
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Primary public API for orchestrating offline-first sync.
 *
 * UI observes [status]; mutations go through [enqueueChange] (outbox + optimistic Room write).
 */
interface SyncManager {

    /** Hot stream of sync lifecycle — ideal for Compose [androidx.compose.runtime.collectAsState]. */
    val status: StateFlow<SyncStatus>

    /** Session state when built-in `auth { }` is configured; otherwise [AuthState.LoggedOut]. */
    val authState: StateFlow<AuthState>

    /** Current session metadata (no raw tokens). Null when logged out. */
    val session: Session?

    /** Register a new user. Request body fields are sent as JSON keys. Requires `auth { }` DSL. */
    suspend fun register(fields: Map<String, String>): AuthResult

    /**
     * Register with [password] wiped in `finally` after the request is built.
     * Prefer over [String] in UI layers that collect credentials in [CharArray].
     */
    suspend fun register(email: String, password: CharArray): AuthResult

    /** Log in. Request body fields are sent as JSON keys. Requires `auth { }` DSL. */
    suspend fun login(fields: Map<String, String>): AuthResult

    /**
     * Log in with [password] wiped in `finally` after the request is built.
     * Prefer over [String] in UI layers that collect credentials in [CharArray].
     */
    suspend fun login(email: String, password: CharArray): AuthResult

    /** Clear local session and call logout endpoint when configured. */
    suspend fun logout(): AuthResult

    /** In-app debug console — outbox, health, conflicts, event log. */
    @ExperimentalSyncForgeApi
    val debug: SyncDebug

    /** Open conflicts awaiting user resolution ([ConflictStrategies.deferToUser]). */
    val conflicts: StateFlow<List<ConflictSummary>>

    /** Full conflict audit trail (open + auto/user resolved) for debug panels. */
    @ExperimentalSyncForgeApi
    val conflictHistory: StateFlow<List<ConflictSummary>>

    /** Full cycle: push pending outbox entries, then pull remote deltas. */
    suspend fun sync(): SyncResult

    /** Push only — flushes the outbox. */
    suspend fun push(): SyncResult

    /** Pull only — fetches remote deltas since last sync cursor. */
    suspend fun pull(): SyncResult

    /**
     * Queue a local mutation. When optimistic updates are enabled, Room is updated immediately;
     * rollback happens automatically if push fails.
     */
    suspend fun enqueueChange(change: Change<*>)

    /** Register WorkManager periodic sync (Android). No-op on unsupported platforms. */
    fun schedulePeriodicSync()

    fun cancelScheduledSync()

    /** Resolves a deferred conflict and updates Room + conflict store. */
    suspend fun resolveConflict(
        entityType: String,
        entityId: String,
        choice: ConflictChoice,
    )

    /** Returns persisted JSON snapshots for an open conflict, if any. */
    suspend fun findOpenConflict(
        entityType: String,
        entityId: String,
    ): ConflictRecord?
}