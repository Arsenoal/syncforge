package dev.syncforge.sample.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.compose.SyncConflictChip
import dev.syncforge.compose.SyncDebugLauncher
import dev.syncforge.compose.SyncStatusUiModel
import dev.syncforge.compose.toUiModel
import dev.syncforge.sample.notes.NotesScreen
import dev.syncforge.sample.notes.NotesViewModel
import dev.syncforge.sample.tags.TagsScreen
import dev.syncforge.sample.tags.TagsViewModel
import dev.syncforge.sample.demo.DemoActivityLogPanel
import dev.syncforge.sample.tasks.TasksScreen
import dev.syncforge.sample.tasks.TasksViewModel
import dev.syncforge.sync.SyncManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSyncForgeApi::class)
@Composable
fun SampleApp(
    tasksViewModel: TasksViewModel,
    notesViewModel: NotesViewModel,
    tagsViewModel: TagsViewModel,
    syncManager: SyncManager,
    onSync: () -> Unit,
    onClearLocalData: suspend () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    val syncStatus by syncManager.status.collectAsState()
    val syncUiModel = syncStatus.toUiModel()
    val conflicts by syncManager.conflicts.collectAsState()

    val title = when {
        destination?.hasRoute<TasksRoute>() == true -> "Tasks"
        destination?.hasRoute<NotesRoute>() == true -> "Notes"
        destination?.hasRoute<TagsRoute>() == true -> "Tags"
        else -> "SyncForge"
    }

    SyncDebugLauncher(syncManager = syncManager) {
        Scaffold(
            topBar = {
                SampleTopBar(
                    title = title,
                    syncUiModel = syncUiModel,
                    conflictCount = conflicts.size,
                    onSync = onSync,
                    onOpenConflict = tasksViewModel::openFirstConflict,
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = destination?.hasRoute<TasksRoute>() == true,
                        onClick = {
                            navController.navigate(TasksRoute) {
                                popUpTo(TasksRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Tasks"
                            )
                        },
                        label = { Text("Tasks") },
                        modifier = Modifier.testTag("nav_tasks"),
                    )
                    NavigationBarItem(
                        selected = destination?.hasRoute<NotesRoute>() == true,
                        onClick = {
                            navController.navigate(NotesRoute) {
                                popUpTo(TasksRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Notes") },
                        label = { Text("Notes") },
                        modifier = Modifier.testTag("nav_notes"),
                    )
                    NavigationBarItem(
                        selected = destination?.hasRoute<TagsRoute>() == true,
                        onClick = {
                            navController.navigate(TagsRoute) {
                                popUpTo(TasksRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Tags") },
                        label = { Text("Tags") },
                        modifier = Modifier.testTag("nav_tags"),
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                DemoActivityLogPanel(
                    onClearLocalData = { scope.launch { onClearLocalData() } },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                NavHost(
                    navController = navController,
                    startDestination = TasksRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<TasksRoute> {
                        TasksScreen(viewModel = tasksViewModel, showTopBar = false)
                    }
                    composable<NotesRoute> {
                        NotesScreen(viewModel = notesViewModel)
                    }
                    composable<TagsRoute> {
                        TagsScreen(viewModel = tagsViewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleTopBar(
    title: String,
    syncUiModel: SyncStatusUiModel,
    conflictCount: Int,
    onSync: () -> Unit,
    onOpenConflict: () -> Unit,
) {
    TopAppBar(
        title = {
            ColumnTitle(title = title, syncLabel = syncUiModel.label, isError = syncUiModel.isError)
        },
        actions = {
            SyncConflictChip(count = conflictCount, onClick = onOpenConflict)
            TextButton(
                onClick = onSync,
                modifier = Modifier.testTag("sync_button"),
            ) {
                Text(if (syncUiModel.isSyncing) "Syncing…" else "Sync")
            }
        },
    )
}

@Composable
private fun ColumnTitle(
    title: String,
    syncLabel: String,
    isError: Boolean,
) {
    androidx.compose.foundation.layout.Column {
        Text(text = title)
        Text(
            text = syncLabel,
            modifier = Modifier.testTag("sync_status_label"),
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}