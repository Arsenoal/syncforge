package dev.syncforge.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.syncforge.api.ExperimentalSyncForgeApi
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
            Text(
                text = "Read-only view — enable SyncDebugLauncher in debug builds for full inspection.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}