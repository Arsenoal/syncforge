package dev.syncforge.mockserver

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PushRejectionDto
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.RemoteDeltaDto
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class InMemorySyncStore {

    private data class Record(
        val entityType: String,
        val entityId: String,
        val payloadJson: String?,
        val serverVersion: Long,
        val updatedAtMillis: Long,
        val isDeleted: Boolean,
    )

    private val records = ConcurrentHashMap<String, Record>()
    private val versionCounter = AtomicLong(1L)

    fun push(entries: List<OutboxEntryDto>, nowMillis: Long): PushResponse {
        val acknowledged = mutableListOf<Long>()
        val rejected = mutableListOf<PushRejectionDto>()

        entries.forEach { entry ->
            if (entry.entityType.isBlank() || entry.entityId.isBlank()) {
                rejected += PushRejectionDto(
                    outboxId = entry.id,
                    code = "VALIDATION",
                    message = "entityType and entityId are required",
                )
                return@forEach
            }

            val key = recordKey(entry.entityType, entry.entityId)
            val nextVersion = versionCounter.getAndIncrement()
            val record = when (entry.changeType) {
                ChangeType.DELETE -> Record(
                    entityType = entry.entityType,
                    entityId = entry.entityId,
                    payloadJson = null,
                    serverVersion = nextVersion,
                    updatedAtMillis = nowMillis,
                    isDeleted = true,
                )
                ChangeType.CREATE, ChangeType.UPDATE -> Record(
                    entityType = entry.entityType,
                    entityId = entry.entityId,
                    payloadJson = entry.payloadJson,
                    serverVersion = nextVersion,
                    updatedAtMillis = nowMillis,
                    isDeleted = false,
                )
            }
            records[key] = record
            acknowledged += entry.id
        }

        return PushResponse(
            acknowledgedIds = acknowledged,
            rejected = rejected,
        )
    }

    fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        nowMillis: Long,
        limit: Int = Int.MAX_VALUE,
        pageCursor: String? = null,
    ): PullResponse {
        val allDeltas = records.values
            .asSequence()
            .filter { it.entityType in entityTypes }
            .filter { it.updatedAtMillis > sinceTimestampMillis }
            .sortedBy { it.updatedAtMillis }
            .map { record ->
                RemoteDeltaDto(
                    entityType = record.entityType,
                    entityId = record.entityId,
                    payloadJson = record.payloadJson,
                    serverVersion = record.serverVersion,
                    updatedAtMillis = record.updatedAtMillis,
                    isDeleted = record.isDeleted,
                )
            }
            .toList()

        val startIndex = decodeCursor(pageCursor).coerceIn(0, allDeltas.size)
        val endIndex = (startIndex + limit).coerceAtMost(allDeltas.size)
        val page = allDeltas.subList(startIndex, endIndex)
        val hasMore = endIndex < allDeltas.size

        return PullResponse(
            deltas = page,
            serverTimestampMillis = nowMillis,
            hasMore = hasMore,
            nextPageCursor = if (hasMore) encodeCursor(endIndex) else null,
        )
    }

    private fun encodeCursor(index: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(index.toString().toByteArray())

    private fun decodeCursor(cursor: String?): Int {
        if (cursor.isNullOrBlank()) return 0
        return String(Base64.getUrlDecoder().decode(cursor)).toIntOrNull() ?: 0
    }

    fun forceUpdate(
        entityType: String,
        entityId: String,
        payloadJson: String,
        nowMillis: Long,
    ): Boolean {
        val key = recordKey(entityType, entityId)
        val existing = records[key] ?: return false
        if (existing.isDeleted) return false

        val nextVersion = versionCounter.getAndIncrement()
        records[key] = existing.copy(
            payloadJson = payloadJson,
            serverVersion = nextVersion,
            updatedAtMillis = nowMillis,
            isDeleted = false,
        )
        return true
    }

    private fun recordKey(entityType: String, entityId: String): String = "$entityType:$entityId"
}