package dev.syncforge.conflict

/**
 * Last successfully synced entity snapshot used as the merge base for three-way merge.
 */
data class MergeBaseSnapshot(
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val serverVersion: Long?,
    val updatedAtMillis: Long,
    val storedAtMillis: Long,
)