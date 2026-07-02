package dev.syncforge.sample.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(
    private val repository: TagRepository,
) : ViewModel() {

    val tags: StateFlow<List<TagEntity>> =
        repository.observeTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch { repository.addTag(label) }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch { repository.deleteTag(tag) }
    }
}