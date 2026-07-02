package dev.syncforge.sample.tags

import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TagRepository(
    private val tagDao: TagDao,
    private val syncManager: SyncManager,
) {

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    suspend fun addTag(label: String) {
        val now = System.currentTimeMillis()
        val tag = TagEntity(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            localVersion = 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.create(TagEntity.ENTITY_TYPE, tag))
    }

    suspend fun deleteTag(tag: TagEntity) {
        syncManager.enqueueChange(
            Change.delete<TagEntity>(
                entityType = TagEntity.ENTITY_TYPE,
                entityId = tag.id,
                localVersion = tag.localVersion + 1,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun sync() = syncManager.sync()
}