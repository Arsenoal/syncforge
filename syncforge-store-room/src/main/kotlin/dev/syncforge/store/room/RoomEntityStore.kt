package dev.syncforge.store.room

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import dev.syncforge.entity.EntityStore
import dev.syncforge.entity.SyncedEntity

/**
 * [EntityStore] adapter over a Room DAO with the SyncForge CRUD shape.
 *
 * Wire with `@SyncForgeStore` so KSP generates an [dev.syncforge.entity.EntityStoreSyncHandler]:
 * ```
 * @SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
 * class TaskEntityStore(dao: TaskDao, db: AppDatabase) : RoomEntityStore<TaskEntity>(dao, db)
 * ```
 *
 * Pass [database] when the backing store should honor [EntityStore.transaction] via
 * `RoomDatabase.withTransaction`.
 */
open class RoomEntityStore<T : SyncedEntity>(
    private val dao: SyncForgeRoomDao<T>,
    private val database: RoomDatabase? = null,
) : EntityStore<T> {

    override suspend fun findById(id: String): T? = dao.findById(id)

    override suspend fun upsert(entity: T) {
        if (dao.findById(entity.id) == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
        }
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun <R> transaction(block: suspend () -> R): R =
        if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
}

/** Wraps a Room DAO as an [EntityStore] for `@SyncForgeStore` handler generation. */
fun <T : SyncedEntity, D : SyncForgeRoomDao<T>> D.asEntityStore(
    database: RoomDatabase? = null,
): EntityStore<T> = RoomEntityStore(this, database)