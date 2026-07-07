package dev.syncforge.conflict

import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity

/**
 * Describes how to handle a detected conflict during pull.
 */
interface ConflictStrategy {

    suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T>
}

data class ConflictContext<T : SyncedEntity>(
    val entityType: String,
    val local: T,
    val remote: RemoteMetadata,
    val remotePayload: T?,
    /** Last successfully synced entity decoded from [MergeBaseStore], if present. */
    val mergeBasePayload: T? = null,
)

sealed interface ConflictOutcome<out T : SyncedEntity> {

    /** Apply [resolution] to Room immediately. */
    data class Resolved<T : SyncedEntity>(val resolution: ConflictResolution<T>) : ConflictOutcome<T>

    /** Persist conflict for user resolution; mark entity [SyncState.CONFLICT]. */
    data class Deferred<T : SyncedEntity>(
        val local: T,
        val remote: T?,
        val remoteMeta: RemoteMetadata,
    ) : ConflictOutcome<T>
}

enum class ConflictResolutionKind {
    KEEP_LOCAL,
    ACCEPT_REMOTE,
    MERGED,
    DELETE_LOCAL,
}