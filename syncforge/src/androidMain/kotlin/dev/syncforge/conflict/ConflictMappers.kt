package dev.syncforge.conflict

internal fun ConflictEntryEntity.toRecord(): ConflictRecord =
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

internal fun ConflictRecord.toEntity(): ConflictEntryEntity =
    ConflictEntryEntity(
        id = id,
        entityType = entityType,
        entityId = entityId,
        localJson = localJson,
        remoteJson = remoteJson,
        localUpdatedAtMillis = localUpdatedAtMillis,
        remoteServerVersion = remoteServerVersion,
        remoteUpdatedAtMillis = remoteUpdatedAtMillis,
        detectedAtMillis = detectedAtMillis,
        status = status.name,
        resolutionKind = resolutionKind?.name,
    )