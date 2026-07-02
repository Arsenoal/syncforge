package dev.syncforge.model

/**
 * Outcome of a single sync operation (push, pull, or full cycle).
 */
sealed interface SyncResult {

    data class Success(
        val pushed: Int = 0,
        val pulled: Int = 0,
        val conflictsResolved: Int = 0,
        val deleted: Int = 0,
        /** Server timestamp from a successful pull — used to advance the sync cursor. */
        val syncCursorMillis: Long? = null,
    ) : SyncResult

    /**
     * Some work completed but errors occurred (e.g. partial push batch failure).
     */
    data class Partial(
        val success: Success,
        val errors: List<SyncError>,
    ) : SyncResult

    data class Failure(
        val error: SyncError,
    ) : SyncResult
}

/**
 * Structured sync error for logging, UI, and retry decisions.
 */
data class SyncError(
    val code: Code,
    val message: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val cause: Throwable? = null,
    /** Original HTTP status when the error originated from [dev.syncforge.network.KtorSyncTransport]. */
    val httpStatus: Int? = null,
) {
    enum class Code {
        NETWORK,
        AUTH,
        CONFLICT,
        VALIDATION,
        SERVER,
        UNKNOWN,
    }
}