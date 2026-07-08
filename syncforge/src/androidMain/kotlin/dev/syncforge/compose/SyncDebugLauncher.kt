package dev.syncforge.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.sync.SyncManager

/**
 * Hyperion/Chucker-style overlay — wraps app content and shows a floating debug button.
 *
 * ```
 * SyncDebugLauncher(syncManager = syncManager) {
 *     MyApp()
 * }
 * ```
 *
 * Use [panelMode] = [SyncDebugPanelMode.DIAGNOSTIC] for a read-only release/support view, or
 * [useFullScreenDiagnostic] for [SyncHealthDiagnosticScreen].
 */
@ExperimentalSyncForgeApi
@Composable
fun SyncDebugLauncher(
    syncManager: SyncManager,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    panelMode: SyncDebugPanelMode = SyncDebugPanelMode.FULL,
    useFullScreenDiagnostic: Boolean = false,
    content: @Composable () -> Unit,
) {
    var showPanel by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (enabled) {
            SmallFloatingActionButton(
                onClick = { showPanel = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(text = "SF", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showPanel) {
        if (useFullScreenDiagnostic) {
            SyncHealthDiagnosticScreen(
                syncManager = syncManager,
                onDismiss = { showPanel = false },
            )
        } else {
            SyncDebugPanel(
                syncManager = syncManager,
                onDismiss = { showPanel = false },
                mode = panelMode,
            )
        }
    }
}