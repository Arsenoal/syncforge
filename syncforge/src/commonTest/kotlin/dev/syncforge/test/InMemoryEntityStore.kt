package dev.syncforge.test

import dev.syncforge.entity.EntityStore
import dev.syncforge.entity.SyncedEntity

/**
 * Minimal [EntityStore] for unit tests — B4 may promote or extend this module.
 */
class InMemoryEntityStore<T : SyncedEntity> : EntityStore<T> {

    private val rows = linkedMapOf<String, T>()
    val transactionCount = mutableListOf<Unit>()

    override suspend fun findById(id: String): T? = rows[id]

    override suspend fun upsert(entity: T) {
        rows[entity.id] = entity
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
    }

    override suspend fun <R> transaction(block: suspend () -> R): R {
        transactionCount += Unit
        return block()
    }

    fun snapshot(): Map<String, T> = rows.toMap()
}