package dev.syncforge.sample.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.compose.SyncConflictChip
import dev.syncforge.compose.SyncConflictResolutionSheet
import dev.syncforge.compose.SyncDebugLauncher
import dev.syncforge.model.SyncState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSyncForgeApi::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    showTopBar: Boolean = true,
) {
    if (showTopBar) {
        SyncDebugLauncher(syncManager = viewModel.syncManager) {
            Scaffold(
                topBar = { TasksTopBar(viewModel = viewModel) },
            ) { padding ->
                TasksScreenBody(viewModel = viewModel, modifier = Modifier.padding(padding))
            }
        }
    } else {
        TasksScreenBody(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksTopBar(viewModel: TasksViewModel) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    TopAppBar(
        title = { Text("SyncForge Tasks") },
        actions = {
            SyncConflictChip(count = conflicts.size, onClick = viewModel::openFirstConflict)
            TextButton(
                onClick = viewModel::sync,
                modifier = Modifier.testTag("sync_button"),
            ) {
                Text(if (syncStatus.isSyncing) "Syncing…" else "Sync")
            }
        },
    )
}

@Composable
private fun TasksScreenBody(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier,
) {
    val tasks by viewModel.tasks.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val activeConflict by viewModel.activeConflict.collectAsState()
    val remotePreview by viewModel.remotePreview.collectAsState()
    val remoteIsDeleted by viewModel.remoteIsDeleted.collectAsState()
    val devMessage by viewModel.devMessage.collectAsState()
    var newTaskTitle by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            devMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = "Tasks use gitLike { } — title/completed auto-merge when edits differ; " +
                    "same-field clashes and server deletes defer to you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (conflicts.isNotEmpty()) {
                Text(
                    text = "Tap the conflict chip or Resolve on a conflicted task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("new_task_input"),
                    label = { Text("New task") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        viewModel.addTask(newTaskTitle)
                        newTaskTitle = ""
                    },
                    modifier = Modifier.testTag("add_task_button"),
                ) {
                    Text("Add")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { viewModel.toggleTask(task) },
                        onDelete = { viewModel.deleteTask(task) },
                        onSimulateServerEdit = { viewModel.simulateServerEdit(task) },
                        onSimulateServerDelete = { viewModel.simulateServerDelete(task) },
                        onResolveConflict = {
                            conflicts.firstOrNull { it.entityId == task.id }
                                ?.let(viewModel::showConflictSheet)
                        },
                    )
                }
            }
    }

    SyncConflictResolutionSheet(
        conflict = activeConflict,
        localContent = {
            TaskPreview(task = activeConflict?.let { viewModel.localTaskFor(it) }, fallback = "—")
        },
        remoteContent = {
            if (remoteIsDeleted) {
                Text(
                    text = "Deleted on server (tombstone)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                TaskPreview(task = remotePreview, fallback = "—")
            }
        },
        onKeepLocal = viewModel::resolveKeepLocal,
        onAcceptRemote = viewModel::resolveAcceptRemote,
        onDismiss = viewModel::dismissConflictSheet,
    )
}

@Composable
private fun TaskPreview(
    task: TaskEntity?,
    fallback: String,
) {
    if (task == null) {
        Text(text = fallback, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
    Text(
        text = if (task.completed) "Completed" else "Not completed",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSimulateServerEdit: () -> Unit,
    onSimulateServerDelete: () -> Unit,
    onResolveConflict: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("task_checkbox_${task.title}"),
            )
            Column {
                Text(text = task.title)
                Text(
                    text = when (task.syncState) {
                        SyncState.SYNCED -> "Synced"
                        SyncState.PENDING -> "Pending"
                        SyncState.CONFLICT -> "Conflict — tap Resolve"
                        SyncState.FAILED -> "Failed"
                    },
                    modifier = Modifier.testTag("row_sync_state_${task.title}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (task.syncState) {
                        SyncState.CONFLICT -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Row {
            if (task.syncState == SyncState.CONFLICT) {
                TextButton(
                    onClick = onResolveConflict,
                    modifier = Modifier.testTag("resolve_${task.id}"),
                ) { Text("Resolve") }
            } else if (task.syncState == SyncState.SYNCED) {
                TextButton(
                    onClick = onSimulateServerEdit,
                    modifier = Modifier.testTag("server_edit_${task.id}"),
                ) { Text("Server edit") }
                TextButton(
                    onClick = onSimulateServerDelete,
                    modifier = Modifier.testTag("server_delete_${task.id}"),
                ) { Text("Server delete") }
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_${task.id}"),
            ) { Text("Delete") }
        }
    }
}