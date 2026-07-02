package dev.syncforge.sample.ios

import dev.syncforge.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryNoteStore {

    private val mutex = Mutex()
    private val notes = MutableStateFlow<List<SampleNoteEntity>>(emptyList())

    fun observeAll(): Flow<List<SampleNoteEntity>> = notes.asStateFlow()

    fun snapshotAll(): List<SampleNoteEntity> = notes.value

    suspend fun createNote(title: String, body: String, nowMillis: Long): SampleNoteEntity = mutex.withLock {
        val note = SampleNoteEntity(
            id = randomSampleId(),
            title = title.trim(),
            body = body.trim(),
            localVersion = 1,
            updatedAtMillis = nowMillis,
            syncState = SyncState.PENDING,
        )
        notes.value = listOf(note) + notes.value
        note
    }

    suspend fun findById(id: String): SampleNoteEntity? = mutex.withLock {
        notes.value.firstOrNull { it.id == id }
    }

    suspend fun insert(note: SampleNoteEntity) = mutex.withLock {
        notes.value = listOf(note) + notes.value.filter { it.id != note.id }
    }

    suspend fun update(note: SampleNoteEntity) = mutex.withLock {
        notes.value = notes.value.map { if (it.id == note.id) note else it }
    }

    suspend fun deleteById(id: String) = mutex.withLock {
        notes.value = notes.value.filter { it.id != id }
    }
}