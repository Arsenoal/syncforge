package dev.syncforge.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.debug.AuditLogFormat
import dev.syncforge.debug.SyncEvent
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncStatus
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * Debug panel mode — [FULL] exposes all tabs and actions; [DIAGNOSTIC] is read-only overview.
 */
@ExperimentalSyncForgeApi
enum class SyncDebugPanelMode {
    FULL,
    DIAGNOSTIC,
}

private enum class DebugTab(val label: String) {
    OVERVIEW("Overview"),
    OUTBOX("Outbox"),
    CONFLICTS("Conflicts"),
    HISTORY("History"),
}

/**
 * Full-screen debug console with tabs for health, outbox, conflicts, and event history.
 */
@ExperimentalSyncForgeApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SyncDebugPanel(
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    mode: SyncDebugPanelMode = SyncDebugPanelMode.FULL,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearOutboxConfirm by remember { mutableStateOf(false) }
    var exportedAuditText by remember { mutableStateOf<String?>(null) }

    val health by syncManager.debug.health.collectAsState()
    val outboxItems by syncManager.debug.outboxItems.collectAsState()
    val conflictRecords by syncManager.debug.conflictRecords.collectAsState()
    val events by syncManager.debug.events.collectAsState()

    val tabs = when (mode) {
        SyncDebugPanelMode.FULL -> DebugTab.entries
        SyncDebugPanelMode.DIAGNOSTIC -> listOf(DebugTab.OVERVIEW)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (mode == SyncDebugPanelMode.FULL) "SyncForge Debug" else "Sync diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            if (tabs.size > 1) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (tabs.getOrElse(selectedTab) { DebugTab.OVERVIEW }) {
                    DebugTab.OVERVIEW -> SyncHealthDashboard(
                        health = health,
                        recentErrors = events.filter { !it.success && it.errorCode != null }.take(5),
                    )
                    DebugTab.OUTBOX -> OutboxTab(items = outboxItems, maxRetries = health.maxRetries)
                    DebugTab.CONFLICTS -> ConflictsTab(
                        records = conflictRecords,
                        onExport = { format, includePayloads ->
                            exportedAuditText = syncManager.debug.exportConflictAudit(
                                format = format,
                                includePayloads = includePayloads,
                            )
                        },
                    )
                    DebugTab.HISTORY -> HistoryTab(
                        events = events,
                        onClearHistory = { scope.launch { syncManager.debug.clearEventLog() } },
                        readOnly = false,
                    )
                }
            }

            if (mode == SyncDebugPanelMode.FULL) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { scope.launch { syncManager.sync() } }) { Text("Sync") }
                    OutlinedButton(onClick = { scope.launch { syncManager.push() } }) { Text("Push") }
                    OutlinedButton(onClick = { scope.launch { syncManager.pull() } }) { Text("Pull") }
                    OutlinedButton(onClick = { showClearOutboxConfirm = true }) { Text("Clear outbox") }
                }
            }
        }
    }

    exportedAuditText?.let { text ->
        AuditExportDialog(
            text = text,
            onDismiss = { exportedAuditText = null },
        )
    }

    if (showClearOutboxConfirm) {
        AlertDialog(
            onDismissRequest = { showClearOutboxConfirm = false },
            title = { Text("Clear outbox?") },
            text = {
                Text(
                    "Removes all pending outbox entries. Optimistic Room writes are not rolled back.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { syncManager.debug.clearOutbox() }
                        showClearOutboxConfirm = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOutboxConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun OutboxTab(items: List<OutboxEntry>, maxRetries: Int) {
    if (items.isEmpty()) {
        EmptyTabMessage("Outbox is empty")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { entry ->
            OutboxRow(entry = entry, maxRetries = maxRetries)
        }
    }
}

@Composable
private fun OutboxRow(entry: OutboxEntry, maxRetries: Int) {
    val stateLabel = when {
        entry.isPermanentlyFailed(maxRetries) -> "FAILED"
        entry.lastError != null -> "RETRY"
        else -> "READY"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "#${entry.id} · ${entry.changeType} · $stateLabel",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${entry.entityType}/${entry.entityId.take(12)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            entry.lastError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            entry.payloadJson?.let { payload ->
                Text(
                    text = payload.take(120) + if (payload.length > 120) "…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ConflictsTab(
    records: List<ConflictRecord>,
    onExport: (AuditLogFormat, includePayloads: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { onExport(AuditLogFormat.JSON, true) }) {
            Text("Export JSON")
        }
        OutlinedButton(onClick = { onExport(AuditLogFormat.CSV, true) }) {
            Text("Export CSV")
        }
    }
    if (records.isEmpty()) {
        EmptyTabMessage("No conflicts recorded")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.id }) { record ->
            ConflictRecordRow(record = record)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictRecordRow(record: ConflictRecord) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${record.entityType}/${record.entityId.take(12)} · ${record.status.name}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Resolution: ${record.resolutionKind?.name ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                Text(text = "Local:", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = record.localJson,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                record.remoteJson?.let { remote ->
                    Text(text = "Remote:", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = remote,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                Text(
                    text = "Tap to inspect JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    events: List<SyncEvent>,
    onClearHistory: () -> Unit,
    readOnly: Boolean,
) {
    if (!readOnly) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClearHistory) { Text("Clear history") }
        }
    }
    if (events.isEmpty()) {
        EmptyTabMessage("No sync events yet")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.id }) { event ->
            EventRow(event = event)
        }
    }
}

@Composable
private fun EventRow(event: SyncEvent) {
    val icon = if (event.success) "✓" else "✗"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = icon,
                color = if (event.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Column {
                Text(
                    text = "${event.type.name} · ${event.summary}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = buildString {
                        append(event.timestampMillis)
                        event.durationMillis?.let { append(" · ${it}ms") }
                        event.errorCode?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuditExportDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conflict audit export") },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    onDismiss()
                },
            ) {
                Text("Copy & close")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun EmptyTabMessage(message: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}