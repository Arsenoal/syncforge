package dev.syncforge.sample.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.syncforge.sample.demo.DemoActivityLog
import dev.syncforge.sample.tasks.DevSyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(
    private val repository: TagRepository,
) : ViewModel() {

    val tags: StateFlow<List<TagEntity>> =
        repository.observeTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _devMessage = MutableStateFlow<String?>(null)
    val devMessage: StateFlow<String?> = _devMessage.asStateFlow()

    fun addTag(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch { repository.addTag(label) }
    }

    fun applyLocalEdit(tag: TagEntity) {
        viewModelScope.launch {
            repository.updateLabel(tag, tagLocalEditLabel(tag.label))
        }
    }

    fun simulateServerEdit(tag: TagEntity) {
        viewModelScope.launch {
            val remoteLabel = tagServerEditLabel(tag.label)
            DevSyncClient.simulateServerEdit(tag, remoteLabel).onSuccess {
                DemoActivityLog.log(
                    "Mock-server POST /dev/simulate-edit — tag label bumped on server",
                    highlight = true,
                )
                _devMessage.value =
                    "Server updated tag — edit locally, then Sync (last-write-wins by timestamp)"
            }.onFailure { error ->
                _devMessage.value = error.message ?: "Simulate tag edit failed"
            }
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch { repository.deleteTag(tag) }
    }

    fun clearDevMessage() {
        _devMessage.value = null
    }
}