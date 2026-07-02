package dev.syncforge.conflict

enum class ConflictStatus {
    OPEN,
    AUTO_RESOLVED,
    USER_RESOLVED,
}

data class ConflictRecord(
    val id: Long,
    val entityType: String,
    val entityId: String,
    val localJson: String,
    val remoteJson: String?,
    val localUpdatedAtMillis: Long,
    val remoteServerVersion: Long,
    val remoteUpdatedAtMillis: Long,
    val detectedAtMillis: Long,
    val status: ConflictStatus,
    val resolutionKind: ConflictResolutionKind?,
)

data class ConflictSummary(
    val id: Long,
    val entityType: String,
    val entityId: String,
    val detectedAtMillis: Long,
    val localUpdatedAtMillis: Long,
    val remoteUpdatedAtMillis: Long,
    val status: ConflictStatus,
    val resolutionKind: ConflictResolutionKind?,
)

fun ConflictRecord.toSummary(): ConflictSummary =
    ConflictSummary(
        id = id,
        entityType = entityType,
        entityId = entityId,
        detectedAtMillis = detectedAtMillis,
        localUpdatedAtMillis = localUpdatedAtMillis,
        remoteUpdatedAtMillis = remoteUpdatedAtMillis,
        status = status,
        resolutionKind = resolutionKind,
    )