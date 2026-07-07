package dev.syncforge.entity

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * [TypedEntitySyncHandler] backed by an [EntityStore] instead of DAO-shaped insert/update/delete.
 *
 * Subclasses supply JSON mapping and [withSyncState]; CRUD delegates to [store].
 */
@ExperimentalSyncForgeApi
abstract class EntityStoreSyncHandler<T : SyncedEntity>(
    protected val store: EntityStore<T>,
) : TypedEntitySyncHandler<T>() {

    override suspend fun findById(id: String): T? = store.findById(id)

    override suspend fun insert(entity: T) {
        store.upsert(entity)
    }

    override suspend fun update(entity: T) {
        store.upsert(entity)
    }

    override suspend fun deleteById(id: String) {
        store.delete(id)
    }
}