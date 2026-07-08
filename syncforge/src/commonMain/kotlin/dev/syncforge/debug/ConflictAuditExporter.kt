package dev.syncforge.debug

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.conflict.ConflictSummary
import dev.syncforge.sync.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes conflict history for support tickets — CSV or JSON (1.5-06).
 */
@ExperimentalSyncForgeApi
object ConflictAuditExporter {

    private const val SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun exportSummaries(
        summaries: List<ConflictSummary>,
        format: AuditLogFormat,
        exportedAtMillis: Long = currentTimeMillis(),
    ): String = exportEntries(
        entries = summaries.map { it.toEntry() },
        format = format,
        exportedAtMillis = exportedAtMillis,
        includePayloads = false,
    )

    fun exportRecords(
        records: List<ConflictRecord>,
        format: AuditLogFormat,
        includePayloads: Boolean = true,
        exportedAtMillis: Long = currentTimeMillis(),
    ): String = exportEntries(
        entries = records.map { it.toEntry(includePayloads) },
        format = format,
        exportedAtMillis = exportedAtMillis,
        includePayloads = includePayloads,
    )

    private fun exportEntries(
        entries: List<ConflictAuditEntry>,
        format: AuditLogFormat,
        exportedAtMillis: Long,
        includePayloads: Boolean,
    ): String = when (format) {
        AuditLogFormat.JSON -> json.encodeToString(
            ConflictAuditDocument(
                schemaVersion = SCHEMA_VERSION,
                exportedAtMillis = exportedAtMillis,
                entryCount = entries.size,
                includePayloads = includePayloads,
                entries = entries,
            ),
        )
        AuditLogFormat.CSV -> toCsv(entries, includePayloads)
    }

    private fun toCsv(entries: List<ConflictAuditEntry>, includePayloads: Boolean): String {
        val headers = buildList {
            addAll(
                listOf(
                    "id",
                    "entityType",
                    "entityId",
                    "detectedAtMillis",
                    "localUpdatedAtMillis",
                    "remoteUpdatedAtMillis",
                    "remoteServerVersion",
                    "status",
                    "resolutionKind",
                ),
            )
            if (includePayloads) {
                add("localJson")
                add("remoteJson")
            }
        }
        return buildString {
            appendLine(headers.joinToString(",") { escapeCsv(it) })
            entries.forEach { entry ->
                val row = buildList {
                    add(entry.id.toString())
                    add(entry.entityType)
                    add(entry.entityId)
                    add(entry.detectedAtMillis.toString())
                    add(entry.localUpdatedAtMillis.toString())
                    add(entry.remoteUpdatedAtMillis.toString())
                    add(entry.remoteServerVersion?.toString().orEmpty())
                    add(entry.status)
                    add(entry.resolutionKind.orEmpty())
                    if (includePayloads) {
                        add(entry.localJson.orEmpty())
                        add(entry.remoteJson.orEmpty())
                    }
                }
                appendLine(row.joinToString(",") { escapeCsv(it) })
            }
        }.trimEnd()
    }

    private fun escapeCsv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun ConflictSummary.toEntry(): ConflictAuditEntry =
        ConflictAuditEntry(
            id = id,
            entityType = entityType,
            entityId = entityId,
            detectedAtMillis = detectedAtMillis,
            localUpdatedAtMillis = localUpdatedAtMillis,
            remoteUpdatedAtMillis = remoteUpdatedAtMillis,
            remoteServerVersion = null,
            status = status.name,
            resolutionKind = resolutionKind?.name,
            localJson = null,
            remoteJson = null,
        )

    private fun ConflictRecord.toEntry(includePayloads: Boolean): ConflictAuditEntry =
        ConflictAuditEntry(
            id = id,
            entityType = entityType,
            entityId = entityId,
            detectedAtMillis = detectedAtMillis,
            localUpdatedAtMillis = localUpdatedAtMillis,
            remoteUpdatedAtMillis = remoteUpdatedAtMillis,
            remoteServerVersion = remoteServerVersion,
            status = status.name,
            resolutionKind = resolutionKind?.name,
            localJson = localJson.takeIf { includePayloads },
            remoteJson = remoteJson.takeIf { includePayloads },
        )
}

@Serializable
private data class ConflictAuditDocument(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val entryCount: Int,
    val includePayloads: Boolean,
    val entries: List<ConflictAuditEntry>,
)

@Serializable
private data class ConflictAuditEntry(
    val id: Long,
    val entityType: String,
    val entityId: String,
    val detectedAtMillis: Long,
    val localUpdatedAtMillis: Long,
    val remoteUpdatedAtMillis: Long,
    val remoteServerVersion: Long? = null,
    val status: String,
    val resolutionKind: String? = null,
    val localJson: String? = null,
    val remoteJson: String? = null,
)