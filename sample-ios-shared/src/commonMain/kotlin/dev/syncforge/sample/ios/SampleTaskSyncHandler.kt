package dev.syncforge.sample.ios

import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncState
import kotlinx.serialization.json.Json

class SampleTaskSyncHandler(
    private val store: InMemoryTaskStore,
) : TypedEntitySyncHandler<SampleTaskEntity>() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val entityType: String = SampleTaskEntity.ENTITY_TYPE

    override fun toJson(entity: SampleTaskEntity): String = json.encodeToString(entity)

    override fun fromJson(jsonString: String): SampleTaskEntity = json.decodeFromString(jsonString)

    override suspend fun findById(id: String): SampleTaskEntity? = store.findById(id)

    override suspend fun insert(entity: SampleTaskEntity) = store.insert(entity)

    override suspend fun update(entity: SampleTaskEntity) = store.update(entity)

    override suspend fun deleteById(id: String) = store.deleteById(id)

    override fun withSyncState(entity: SampleTaskEntity, state: SyncState): SampleTaskEntity =
        entity.copy(syncState = state)
}