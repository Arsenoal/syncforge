package dev.syncforge.outbox

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry

internal fun OutboxEntryEntity.toModel(): OutboxEntry =
    OutboxEntry(
        id = id,
        entityType = entityType,
        entityId = entityId,
        changeType = ChangeType.valueOf(changeType),
        payloadJson = payloadJson,
        rollbackSnapshotJson = rollbackSnapshotJson,
        localVersion = localVersion,
        createdAtMillis = createdAtMillis,
        retryCount = retryCount,
        lastError = lastError,
        nextRetryAtMillis = nextRetryAtMillis,
    )

internal fun OutboxEntry.toEntity(): OutboxEntryEntity =
    OutboxEntryEntity(
        id = id,
        entityType = entityType,
        entityId = entityId,
        changeType = changeType.name,
        payloadJson = payloadJson,
        rollbackSnapshotJson = rollbackSnapshotJson,
        localVersion = localVersion,
        createdAtMillis = createdAtMillis,
        retryCount = retryCount,
        lastError = lastError,
        nextRetryAtMillis = nextRetryAtMillis,
    )