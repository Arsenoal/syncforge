package dev.syncforge.transport.contract

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushRejectionDto
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.RemoteDeltaDto
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * KMP in-memory push/pull backend for contract tests and local prototyping.
 * Mirrors [dev.syncforge.server.InMemorySyncStore] semantics without JVM-only APIs.
 */
@OptIn(ExperimentalEncodingApi::class)
class InMemorySyncBackend {

    private data class Record(
        val entityType: String,
        val entityId: String,
        val payloadJson: String?,
        val serverVersion: Long,
        val updatedAtMillis: Long,
        val isDeleted: Boolean,
    )

    private val records = mutableMapOf<String, Record>()
    private var versionCounter = 1L
    private var lastNowMillis = 0L

    fun push(entries: List<OutboxEntryDto>, nowMillis: Long): PushResponse {
        lastNowMillis = nowMillis
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
            val existing = records[key]
            if (
                existing?.isDeleted == true &&
                (entry.changeType == ChangeType.CREATE || entry.changeType == ChangeType.UPDATE)
            ) {
                rejected += PushRejectionDto(
                    outboxId = entry.id,
                    code = "CONFLICT",
                    message = "Entity was deleted on the server",
                )
                return@forEach
            }

            if (
                entry.changeType == ChangeType.UPDATE &&
                existing != null &&
                existing.serverVersion != entry.localVersion - 1
            ) {
                rejected += PushRejectionDto(
                    outboxId = entry.id,
                    code = "CONFLICT",
                    message =
                        "Server version ${existing.serverVersion} does not match expected base ${entry.localVersion - 1}",
                )
                return@forEach
            }

            val nextVersion = versionCounter++
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
        pageSize: Int,
        pageCursor: String?,
    ): PullResponse {
        lastNowMillis = nowMillis
        val allDeltas = records.values
            .asSequence()
            .filter { entityTypes.isEmpty() || it.entityType in entityTypes }
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
        val endIndex = (startIndex + pageSize).coerceAtMost(allDeltas.size)
        val page = allDeltas.subList(startIndex, endIndex)
        val hasMore = endIndex < allDeltas.size

        return PullResponse(
            deltas = page,
            serverTimestampMillis = nowMillis,
            hasMore = hasMore,
            nextPageCursor = if (hasMore) encodeCursor(endIndex) else null,
        )
    }

    fun forceDelete(entityType: String, entityId: String, nowMillis: Long): Boolean {
        lastNowMillis = nowMillis
        val key = recordKey(entityType, entityId)
        val existing = records[key] ?: return false
        if (existing.isDeleted) return true

        val nextVersion = versionCounter++
        records[key] = existing.copy(
            payloadJson = null,
            serverVersion = nextVersion,
            updatedAtMillis = nowMillis,
            isDeleted = true,
        )
        return true
    }

    fun clear() {
        records.clear()
        versionCounter = 1L
        lastNowMillis = 0L
    }

    fun lastNowMillis(): Long = lastNowMillis

    private fun encodeCursor(index: Int): String =
        Base64.UrlSafe.encode(index.toString().encodeToByteArray())

    private fun decodeCursor(cursor: String?): Int {
        if (cursor.isNullOrBlank()) return 0
        return Base64.UrlSafe.decode(cursor).decodeToString().toIntOrNull() ?: 0
    }

    private fun recordKey(entityType: String, entityId: String): String = "$entityType:$entityId"
}