package dev.syncforge.entity

/**
 * App-side persistence port for a single synced entity type.
 *
 * Implement for Room, Realm, SQLDelight, in-memory, or custom storage. Wire into sync via
 * [EntityStoreSyncHandler] or a hand-written [EntitySyncHandler].
 *
 * SyncForge's internal outbox and conflict databases remain separate (SQLDelight `syncforge.db`).
 */
interface EntityStore<T : SyncedEntity> {

    suspend fun findById(id: String): T?

    /** Insert or replace the row keyed by [SyncedEntity.id]. */
    suspend fun upsert(entity: T)

    suspend fun delete(id: String)

    /**
     * Runs [block] inside a store transaction when the backing database supports it.
     * Default implementation runs [block] directly with no extra isolation.
     */
    suspend fun <R> transaction(block: suspend () -> R): R = block()
}