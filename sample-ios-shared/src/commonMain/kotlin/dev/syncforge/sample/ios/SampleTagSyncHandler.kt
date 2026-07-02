package dev.syncforge.sample.ios

import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncState
import kotlinx.serialization.json.Json

class SampleTagSyncHandler(
    private val store: InMemoryTagStore,
) : TypedEntitySyncHandler<SampleTagEntity>() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val entityType: String = SampleTagEntity.ENTITY_TYPE

    override fun toJson(entity: SampleTagEntity): String = json.encodeToString(entity)

    override fun fromJson(jsonString: String): SampleTagEntity = json.decodeFromString(jsonString)

    override suspend fun findById(id: String): SampleTagEntity? = store.findById(id)

    override suspend fun insert(entity: SampleTagEntity) = store.insert(entity)

    override suspend fun update(entity: SampleTagEntity) = store.update(entity)

    override suspend fun deleteById(id: String) = store.deleteById(id)

    override fun withSyncState(entity: SampleTagEntity, state: SyncState): SampleTagEntity =
        entity.copy(syncState = state)
}