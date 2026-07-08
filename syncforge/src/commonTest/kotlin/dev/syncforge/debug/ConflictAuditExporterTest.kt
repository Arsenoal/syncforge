package dev.syncforge.debug

import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.conflict.ConflictResolutionKind
import dev.syncforge.conflict.ConflictStatus
import dev.syncforge.conflict.ConflictSummary
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictAuditExporterTest {

    @Test
    fun exportSummaries_json_containsMetadataAndEntries() {
        val json = ConflictAuditExporter.exportSummaries(
            summaries = listOf(sampleSummary()),
            format = AuditLogFormat.JSON,
            exportedAtMillis = 9_000L,
        )
        assertContains(json, "\"schemaVersion\": 1")
        assertContains(json, "\"exportedAtMillis\": 9000")
        assertContains(json, "\"entityType\": \"tasks\"")
        assertContains(json, "\"status\": \"OPEN\"")
        assertFalse(json.contains("localJson"))
    }

    @Test
    fun exportRecords_csv_escapesCommasAndQuotes() {
        val payload = "{\"title\":\"a,b\",\"note\":\"say \\\"hi\\\"\"}"
        val csv = ConflictAuditExporter.exportRecords(
            records = listOf(sampleRecord(localJson = payload)),
            format = AuditLogFormat.CSV,
            includePayloads = true,
            exportedAtMillis = 1L,
        )
        assertTrue(csv.startsWith("id,entityType,entityId,"))
        assertContains(csv, "a,b")
        assertContains(csv, "say")
    }

    @Test
    fun exportRecords_withoutPayloads_omitsJsonColumns() {
        val csv = ConflictAuditExporter.exportRecords(
            records = listOf(sampleRecord()),
            format = AuditLogFormat.CSV,
            includePayloads = false,
        )
        assertFalse(csv.contains("localJson"))
        assertFalse(csv.contains("remoteJson"))
        assertContains(csv, "tasks,task-1")
    }

    @Test
    fun exportSummaries_emptyList_producesZeroEntryCount() {
        val json = ConflictAuditExporter.exportSummaries(
            summaries = emptyList(),
            format = AuditLogFormat.JSON,
            exportedAtMillis = 1L,
        )
        assertContains(json, "\"entryCount\": 0")
    }

    private fun sampleSummary() = ConflictSummary(
        id = 1L,
        entityType = "tasks",
        entityId = "task-1",
        detectedAtMillis = 100L,
        localUpdatedAtMillis = 90L,
        remoteUpdatedAtMillis = 110L,
        status = ConflictStatus.OPEN,
        resolutionKind = null,
    )

    private fun sampleRecord(localJson: String = "{\"id\":\"task-1\"}") = ConflictRecord(
        id = 1L,
        entityType = "tasks",
        entityId = "task-1",
        localJson = localJson,
        remoteJson = "{\"id\":\"task-1\",\"title\":\"remote\"}",
        localUpdatedAtMillis = 90L,
        remoteServerVersion = 2L,
        remoteUpdatedAtMillis = 110L,
        detectedAtMillis = 100L,
        status = ConflictStatus.USER_RESOLVED,
        resolutionKind = ConflictResolutionKind.KEEP_LOCAL,
    )
}