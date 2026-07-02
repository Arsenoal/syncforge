package dev.syncforge.persistence

import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.conflict.ConflictResolutionKind
import dev.syncforge.conflict.ConflictStatus
import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry

internal fun Syncforge_outbox.toModel(): OutboxEntry =
    OutboxEntry(
        id = id,
        entityType = entityType,
        entityId = entityId,
        changeType = ChangeType.valueOf(changeType),
        payloadJson = payloadJson,
        rollbackSnapshotJson = rollbackSnapshotJson,
        localVersion = localVersion,
        createdAtMillis = createdAtMillis,
        retryCount = retryCount.toInt(),
        lastError = lastError,
        nextRetryAtMillis = nextRetryAtMillis,
    )

internal fun Syncforge_conflicts.toRecord(): ConflictRecord =
    ConflictRecord(
        id = id,
        entityType = entityType,
        entityId = entityId,
        localJson = localJson,
        remoteJson = remoteJson,
        localUpdatedAtMillis = localUpdatedAtMillis,
        remoteServerVersion = remoteServerVersion,
        remoteUpdatedAtMillis = remoteUpdatedAtMillis,
        detectedAtMillis = detectedAtMillis,
        status = ConflictStatus.valueOf(status),
        resolutionKind = resolutionKind?.let { ConflictResolutionKind.valueOf(it) },
    )