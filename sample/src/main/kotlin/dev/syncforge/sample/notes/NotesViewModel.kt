package dev.syncforge.sample.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.syncforge.sample.demo.DemoActivityLog
import dev.syncforge.sample.tags.TagEntity
import dev.syncforge.sample.tags.TagRepository
import dev.syncforge.sample.tasks.DevSyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: NoteRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> =
        repository.observeNotes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagEntity>> =
        tagRepository.observeTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _devMessage = MutableStateFlow<String?>(null)
    val devMessage: StateFlow<String?> = _devMessage.asStateFlow()

    fun addNote(title: String, body: String = "", tagId: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addNote(title, body, tagId) }
    }

    fun applyLocalBodyEdit(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateBody(note, noteLocalEditBody(note.body))
        }
    }

    fun simulateServerEdit(note: NoteEntity) {
        viewModelScope.launch {
            val remoteBody = noteServerEditBody(note.body)
            DevSyncClient.simulateServerEdit(note, remoteBody).onSuccess {
                DemoActivityLog.log(
                    "Mock-server POST /dev/simulate-edit — note body bumped on server",
                    highlight = true,
                )
                _devMessage.value =
                    "Server updated note — edit locally, then Sync (alwaysRemote accepts server copy)"
            }.onFailure { error ->
                _devMessage.value = error.message ?: "Simulate note edit failed"
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note) }
    }

    fun clearDevMessage() {
        _devMessage.value = null
    }
}