package dev.syncforge.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.syncforge.model.SyncStatus
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SyncManager.collectSyncStatusUiModel(): SyncStatusUiModel {
    val status by status.collectAsState()
    return status.toUiModel()
}

@Composable
fun StateFlow<SyncStatus>.collectSyncStatusUiModel(): SyncStatusUiModel {
    val status by collectAsState()
    return status.toUiModel()
}