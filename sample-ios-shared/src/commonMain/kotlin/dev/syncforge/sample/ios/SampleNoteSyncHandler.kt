package dev.syncforge.sample.ios

import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncState
import kotlinx.serialization.json.Json

class SampleNoteSyncHandler(
    private val store: InMemoryNoteStore,
) : TypedEntitySyncHandler<SampleNoteEntity>() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val entityType: String = SampleNoteEntity.ENTITY_TYPE

    override fun toJson(entity: SampleNoteEntity): String = json.encodeToString(entity)

    override fun fromJson(jsonString: String): SampleNoteEntity = json.decodeFromString(jsonString)

    override suspend fun findById(id: String): SampleNoteEntity? = store.findById(id)

    override suspend fun insert(entity: SampleNoteEntity) = store.insert(entity)

    override suspend fun update(entity: SampleNoteEntity) = store.update(entity)

    override suspend fun deleteById(id: String) = store.deleteById(id)

    override fun withSyncState(entity: SampleNoteEntity, state: SyncState): SampleNoteEntity =
        entity.copy(syncState = state)
}