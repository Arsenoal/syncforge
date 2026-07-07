package dev.syncforge.store.room

import dev.syncforge.entity.SyncedEntity

/**
 * Room DAO contract expected by SyncForge KSP for `@SyncForgeDao` handlers.
 *
 * App DAOs can extend this interface so [RoomEntityStore] can wrap them for the
 * `@SyncForgeStore` code path.
 */
interface SyncForgeRoomDao<T : SyncedEntity> {

    suspend fun findById(id: String): T?

    suspend fun insert(entity: T)

    suspend fun update(entity: T)

    suspend fun deleteById(id: String)
}