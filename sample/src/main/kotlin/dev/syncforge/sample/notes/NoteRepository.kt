package dev.syncforge.sample.notes

import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(
    private val noteDao: NoteDao,
    private val syncManager: SyncManager,
) {

    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeAll()

    suspend fun addNote(title: String, body: String = "", tagId: String? = null) {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            body = body.trim(),
            tagId = tagId,
            localVersion = 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.create(NoteEntity.ENTITY_TYPE, note))
    }

    suspend fun updateBody(
        note: NoteEntity,
        newBody: String,
        updatedAtMillis: Long = System.currentTimeMillis(),
    ) {
        val updated = note.copy(
            body = newBody.trim(),
            localVersion = note.localVersion + 1,
            updatedAtMillis = updatedAtMillis,
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.update(NoteEntity.ENTITY_TYPE, updated))
    }

    suspend fun deleteNote(note: NoteEntity) {
        syncManager.enqueueChange(
            Change.delete<NoteEntity>(
                entityType = NoteEntity.ENTITY_TYPE,
                entityId = note.id,
                localVersion = note.localVersion + 1,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun sync() = syncManager.sync()
}