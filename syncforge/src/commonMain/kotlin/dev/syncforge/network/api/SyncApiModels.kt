package dev.syncforge.network.api

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.PushRejection
import dev.syncforge.network.PushResult
import dev.syncforge.network.PullResult
import dev.syncforge.network.RemoteDelta
import kotlinx.serialization.Serializable

@Serializable
data class PushRequest(
    val entries: List<OutboxEntryDto>,
)

@Serializable
data class OutboxEntryDto(
    val id: Long,
    val entityType: String,
    val entityId: String,
    val changeType: ChangeType,
    val payloadJson: String?,
    val localVersion: Long,
    val createdAtMillis: Long,
)

@Serializable
data class PushResponse(
    val acknowledgedIds: List<Long> = emptyList(),
    val rejected: List<PushRejectionDto> = emptyList(),
)

@Serializable
data class PushRejectionDto(
    val outboxId: Long,
    val code: String,
    val message: String,
)

@Serializable
data class PullResponse(
    val deltas: List<RemoteDeltaDto> = emptyList(),
    val serverTimestampMillis: Long,
    val hasMore: Boolean = false,
    val nextPageCursor: String? = null,
)

@Serializable
data class RemoteDeltaDto(
    val entityType: String,
    val entityId: String,
    val payloadJson: String? = null,
    val serverVersion: Long,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false,
)

fun OutboxEntry.toDto(): OutboxEntryDto =
    OutboxEntryDto(
        id = id,
        entityType = entityType,
        entityId = entityId,
        changeType = changeType,
        payloadJson = payloadJson,
        localVersion = localVersion,
        createdAtMillis = createdAtMillis,
    )

fun OutboxEntryDto.toOutboxEntry(): OutboxEntry =
    OutboxEntry(
        id = id,
        entityType = entityType,
        entityId = entityId,
        changeType = changeType,
        payloadJson = payloadJson,
        localVersion = localVersion,
        createdAtMillis = createdAtMillis,
    )

fun PushResponse.toPushResult(): PushResult =
    PushResult(
        acknowledgedIds = acknowledgedIds,
        rejected = rejected.map { it.toPushRejection() },
    )

fun PushRejectionDto.toPushRejection(): PushRejection =
    PushRejection(
        outboxId = outboxId,
        error = SyncError(
            code = code.toSyncErrorCode(),
            message = message,
        ),
    )

fun PullResponse.toPullResult(): PullResult =
    PullResult(
        deltas = deltas.map { it.toRemoteDelta() },
        serverTimestampMillis = serverTimestampMillis,
        hasMore = hasMore,
        nextPageCursor = nextPageCursor,
    )

fun RemoteDeltaDto.toRemoteDelta(): RemoteDelta =
    RemoteDelta(
        entityType = entityType,
        entityId = entityId,
        payloadJson = payloadJson,
        serverVersion = serverVersion,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted,
    )

private fun String.toSyncErrorCode(): SyncError.Code =
    when (uppercase()) {
        "NETWORK" -> SyncError.Code.NETWORK
        "AUTH" -> SyncError.Code.AUTH
        "CONFLICT" -> SyncError.Code.CONFLICT
        "VALIDATION" -> SyncError.Code.VALIDATION
        "SERVER" -> SyncError.Code.SERVER
        else -> SyncError.Code.UNKNOWN
    }