# Conflict audit export

Export conflict history as **CSV** or **JSON** for support tickets and post-incident review (1.5-06).

## API

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)

// From debug panel / diagnostic screen backing store
val payload = syncManager.debug.exportConflictAudit(
    format = AuditLogFormat.JSON,
    includePayloads = false, // true includes localJson / remoteJson
)

// From SyncManager.conflictHistory (summaries only)
val summaries = syncManager.conflictHistory.value
val csv = ConflictAuditExporter.exportSummaries(summaries, AuditLogFormat.CSV)
```

| Parameter | Default | Notes |
|-----------|---------|-------|
| `format` | — | `AuditLogFormat.JSON` or `AuditLogFormat.CSV` |
| `includePayloads` | `false` on `exportConflictAudit` | Full `ConflictRecord` JSON snapshots |

## JSON schema

```json
{
  "schemaVersion": 1,
  "exportedAtMillis": 1710000000000,
  "entryCount": 1,
  "includePayloads": false,
  "entries": [
    {
      "id": 1,
      "entityType": "tasks",
      "entityId": "task-1",
      "detectedAtMillis": 100,
      "localUpdatedAtMillis": 90,
      "remoteUpdatedAtMillis": 110,
      "status": "USER_RESOLVED",
      "resolutionKind": "KEEP_LOCAL"
    }
  ]
}
```

When `includePayloads` is `true`, entries also include `localJson`, `remoteJson`, and `remoteServerVersion`.

## UI

| Surface | Export |
|---------|--------|
| **SyncDebugPanel** → Conflicts tab | JSON / CSV with payloads |
| **SyncHealthDiagnosticScreen** | JSON / CSV summaries only (no entity JSON) |

Exports open in a dialog with **Copy & close** (clipboard).

## Privacy

- Diagnostic / summary exports omit entity payloads — safe for release builds.
- Full exports may contain PII from serialized entities; treat like application logs.

## Related

- [TRACING.md](TRACING.md) — runtime sync spans
- [RATE_LIMITING.md](RATE_LIMITING.md) — retry behavior
- [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md) — resolution kinds in exports