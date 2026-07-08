package dev.syncforge.sample.desktop

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.syncforge.compose.SyncConflictResolutionSheet
import dev.syncforge.conflict.ConflictSummary

/**
 * Desktop Compose demo for shared conflict UI (1.3-05).
 *
 * Run: `./gradlew :sample-desktop:runComposeConflictDemo`
 */
fun main() = application {
    MaterialTheme {
        var active by remember {
            mutableStateOf<ConflictSummary?>(null)
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "SyncForge conflict UI (CMP)",
        ) {
            Button(onClick = { active = sampleConflict }) {
                Text("Show conflict dialog")
            }

            SyncConflictResolutionSheet(
                conflict = active,
                localContent = {
                    Text("Local: Buy milk (completed = false)")
                },
                remoteContent = {
                    Text("Server: Buy oat milk (completed = true)")
                },
                onKeepLocal = { active = null },
                onAcceptRemote = { active = null },
                onDismiss = { active = null },
            )
        }
    }
}

private val sampleConflict = ConflictSummary(
    id = 1L,
    entityType = "tasks",
    entityId = "demo-task-001",
    detectedAtMillis = System.currentTimeMillis(),
    localUpdatedAtMillis = System.currentTimeMillis(),
    remoteUpdatedAtMillis = System.currentTimeMillis() - 1_000,
    status = dev.syncforge.conflict.ConflictStatus.OPEN,
    resolutionKind = null,
)