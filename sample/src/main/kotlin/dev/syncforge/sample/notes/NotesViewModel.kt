package dev.syncforge.sample.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.syncforge.sample.tags.TagEntity
import dev.syncforge.sample.tags.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun addNote(title: String, body: String = "", tagId: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addNote(title, body, tagId) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note) }
    }
}