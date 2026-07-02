package dev.syncforge.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.debug.SyncEvent
import dev.syncforge.debug.SyncHealth
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncStatus
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.launch

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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearOutboxConfirm by remember { mutableStateOf(false) }

    val health by syncManager.debug.health.collectAsState()
    val outboxItems by syncManager.debug.outboxItems.collectAsState()
    val conflictRecords by syncManager.debug.conflictRecords.collectAsState()
    val events by syncManager.debug.events.collectAsState()

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
                Text(text = "SyncForge Debug", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            TabRow(selectedTabIndex = selectedTab) {
                DebugTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (DebugTab.entries[selectedTab]) {
                DebugTab.OVERVIEW -> OverviewTab(health = health)
                DebugTab.OUTBOX -> OutboxTab(items = outboxItems, maxRetries = health.maxRetries)
                DebugTab.CONFLICTS -> ConflictsTab(records = conflictRecords)
                DebugTab.HISTORY -> HistoryTab(
                    events = events,
                    onClearHistory = { scope.launch { syncManager.debug.clearEventLog() } },
                )
            }

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
private fun OverviewTab(health: SyncHealth) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HealthCard(title = "Network", value = if (health.isOnline) "Online" else "Offline")
        HealthCard(title = "Sync status", value = health.status.toDebugLabel())
        HealthCard(title = "Pending outbox", value = health.pendingOutboxCount.toString())
        HealthCard(title = "Failed entries", value = health.failedOutboxCount.toString())
        HealthCard(title = "Open conflicts", value = health.openConflictCount.toString())
        HealthCard(
            title = "Last synced",
            value = health.lastSyncedAtMillis?.toString() ?: "Never",
        )
        HealthCard(title = "Pull cursor", value = health.pullCursorMillis.toString())
    }
}

@Composable
private fun HealthCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
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
private fun ConflictsTab(records: List<ConflictRecord>) {
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClearHistory) { Text("Clear history") }
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
            Text(text = icon, color = if (event.success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            })
            Column {
                Text(
                    text = "${event.type.name} · ${event.summary}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = event.timestampMillis.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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

private fun SyncStatus.toDebugLabel(): String = when (this) {
    SyncStatus.Idle -> "Idle"
    is SyncStatus.Syncing -> "Syncing (${phase.name})"
    is SyncStatus.Pending -> "Pending (outbox=$outboxCount, conflicts=$conflictCount)"
    is SyncStatus.Offline -> "Offline (queued=$outboxCount)"
    is SyncStatus.LastSynced -> "Last synced @ $timestampMillis"
    is SyncStatus.Error -> "Error: $message"
}