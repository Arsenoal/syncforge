package dev.syncforge.model

import kotlinx.serialization.Serializable

/**
 * Persistent outbox row — the source of truth for pending upstream mutations.
 *
 * Stored in Room on Android ([dev.syncforge.outbox.OutboxRepository]).
 * Payload is JSON so the outbox stays entity-agnostic.
 */
@Serializable
data class OutboxEntry(
    val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val changeType: ChangeType,
    val payloadJson: String?,
    /** JSON snapshot captured before optimistic write — used for rollback on push failure. */
    val rollbackSnapshotJson: String? = null,
    val localVersion: Long,
    val createdAtMillis: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
    /** Epoch millis after which a failed entry may be pushed again. */
    val nextRetryAtMillis: Long? = null,
) {
    fun isPermanentlyFailed(maxRetries: Int): Boolean = retryCount >= maxRetries

    fun isReadyForPush(nowMillis: Long, maxRetries: Int): Boolean =
        !isPermanentlyFailed(maxRetries) &&
            (lastError == null || (nextRetryAtMillis != null && nextRetryAtMillis <= nowMillis))
    val isDelete: Boolean get() = changeType == ChangeType.DELETE
}