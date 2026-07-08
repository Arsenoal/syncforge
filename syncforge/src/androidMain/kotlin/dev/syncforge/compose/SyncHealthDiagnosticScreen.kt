package dev.syncforge.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.debug.AuditLogFormat
import dev.syncforge.sync.SyncManager

/**
 * Read-only SyncHealth dashboard for release or support builds (1.5-03).
 *
 * Shows metrics and error breakdown without outbox payloads, conflict JSON, or destructive actions.
 */
@ExperimentalSyncForgeApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHealthDiagnosticScreen(
    syncManager: SyncManager,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val health by syncManager.debug.health.collectAsState()
    val events by syncManager.debug.events.collectAsState()
    val recentErrors = events.filter { !it.success && it.errorCode != null }.take(5)
    var exportedAuditText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Sync diagnostics") },
                actions = {
                    onDismiss?.let { dismiss ->
                        TextButton(onClick = dismiss) { Text("Close") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SyncHealthDashboard(
                health = health,
                recentErrors = recentErrors,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        exportedAuditText = syncManager.debug.exportConflictAudit(
                            format = AuditLogFormat.JSON,
                            includePayloads = false,
                        )
                    },
                ) {
                    Text("Export conflicts (JSON)")
                }
                OutlinedButton(
                    onClick = {
                        exportedAuditText = syncManager.debug.exportConflictAudit(
                            format = AuditLogFormat.CSV,
                            includePayloads = false,
                        )
                    },
                ) {
                    Text("Export conflicts (CSV)")
                }
            }
            Text(
                text = "Read-only view — enable SyncDebugLauncher in debug builds for full inspection.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    exportedAuditText?.let { text ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { exportedAuditText = null },
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
                        exportedAuditText = null
                    },
                ) {
                    Text("Copy & close")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedAuditText = null }) { Text("Close") }
            },
        )
    }
}