package dev.syncforge.sample.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.syncforge.compose.SyncStatusUiModel
import dev.syncforge.compose.toUiModel
import dev.syncforge.conflict.ConflictChoice
import dev.syncforge.conflict.ConflictSummary
import dev.syncforge.sample.tasks.DevSyncClient
import dev.syncforge.sample.tasks.TaskEntity
import dev.syncforge.sample.tasks.TaskRepository
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class TasksViewModel(
    private val repository: TaskRepository,
    val syncManager: SyncManager,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val tasks: StateFlow<List<TaskEntity>> =
        repository.observeTasks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncStatus: StateFlow<SyncStatusUiModel> =
        syncManager.status
            .map { it.toUiModel() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                syncManager.status.value.toUiModel(),
            )

    val conflicts: StateFlow<List<ConflictSummary>> =
        syncManager.conflicts
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeConflict = MutableStateFlow<ConflictSummary?>(null)
    val activeConflict: StateFlow<ConflictSummary?> = _activeConflict.asStateFlow()

    private val _remotePreview = MutableStateFlow<TaskEntity?>(null)
    val remotePreview: StateFlow<TaskEntity?> = _remotePreview.asStateFlow()

    private val _devMessage = MutableStateFlow<String?>(null)
    val devMessage: StateFlow<String?> = _devMessage.asStateFlow()

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addTask(title) }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch { repository.toggleCompleted(task) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { repository.deleteTask(task) }
    }

    fun sync() {
        viewModelScope.launch { repository.sync() }
    }

    fun showConflictSheet(conflict: ConflictSummary) {
        viewModelScope.launch {
            _activeConflict.value = conflict
            val record = syncManager.findOpenConflict(conflict.entityType, conflict.entityId)
            _remotePreview.value = record?.remoteJson?.let { json.decodeFromString<TaskEntity>(it) }
        }
    }

    fun dismissConflictSheet() {
        _activeConflict.value = null
        _remotePreview.value = null
    }

    fun resolveKeepLocal() {
        val conflict = _activeConflict.value ?: return
        viewModelScope.launch {
            syncManager.resolveConflict(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                choice = ConflictChoice.KeepLocal,
            )
            dismissConflictSheet()
        }
    }

    fun resolveAcceptRemote() {
        val conflict = _activeConflict.value ?: return
        viewModelScope.launch {
            syncManager.resolveConflict(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                choice = ConflictChoice.AcceptRemote,
            )
            dismissConflictSheet()
        }
    }

    fun openFirstConflict() {
        conflicts.value.firstOrNull()?.let { showConflictSheet(it) }
    }

    fun simulateServerEdit(task: TaskEntity) {
        viewModelScope.launch {
            DevSyncClient.simulateServerEdit(
                task = task,
                newTitle = "${task.title} (server edit)",
            ).onSuccess {
                _devMessage.value = "Server updated — edit locally, then Sync to trigger conflict"
            }.onFailure { error ->
                _devMessage.value = error.message ?: "Simulate edit failed"
            }
        }
    }

    fun clearDevMessage() {
        _devMessage.value = null
    }

    fun localTaskFor(conflict: ConflictSummary): TaskEntity? =
        tasks.value.firstOrNull { it.id == conflict.entityId }
}