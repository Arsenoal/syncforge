package dev.syncforge.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.syncforge.conflict.ConflictSummary

/**
 * Tappable chip shown when [count] > 0 — opens conflict resolution UI.
 *
 * Compose Multiplatform — available on Android, JVM/desktop, and Apple targets (1.3-05).
 */
@Composable
fun SyncConflictChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return

    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(if (count == 1) "1 conflict" else "$count conflicts")
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    )
}

/**
 * Cross-platform dialog for resolving a deferred conflict. Supply [localContent] and [remoteContent]
 * to render entity-specific previews (title, fields, etc.).
 *
 * Uses [AlertDialog] for JVM/desktop and Apple; Android sample apps may wrap in Material theme from
 * either androidx or org.jetbrains.compose stacks.
 */
@Composable
fun SyncConflictResolutionSheet(
    conflict: ConflictSummary?,
    localContent: @Composable () -> Unit,
    remoteContent: @Composable () -> Unit,
    onKeepLocal: () -> Unit,
    onAcceptRemote: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (conflict == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Resolve conflict",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "${conflict.entityType} · ${conflict.entityId.take(8)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConflictSideCard(title = "Your version", content = localContent)
                ConflictSideCard(title = "Server version", content = remoteContent)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onKeepLocal,
                modifier = Modifier.testTag("conflict_keep_local"),
            ) {
                Text("Keep mine")
            }
        },
        confirmButton = {
            Button(
                onClick = onAcceptRemote,
                modifier = Modifier.testTag("conflict_accept_remote"),
            ) {
                Text("Use server")
            }
        },
    )
}

@Composable
private fun ConflictSideCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}