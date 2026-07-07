package dev.syncforge.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.syncforge.conflict.ConflictSummary

/**
 * Tappable chip shown when [count] > 0 — opens conflict resolution UI.
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
 * Bottom sheet for resolving a deferred conflict. Supply [localContent] and [remoteContent]
 * to render entity-specific previews (title, fields, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Resolve conflict",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${conflict.entityType} · ${conflict.entityId.take(8)}…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ConflictSideCard(title = "Your version", content = localContent)
            ConflictSideCard(title = "Server version", content = remoteContent)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onKeepLocal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("conflict_keep_local"),
                ) {
                    Text("Keep mine")
                }
                Button(
                    onClick = onAcceptRemote,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("conflict_accept_remote"),
                ) {
                    Text("Use server")
                }
            }
        }
    }
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