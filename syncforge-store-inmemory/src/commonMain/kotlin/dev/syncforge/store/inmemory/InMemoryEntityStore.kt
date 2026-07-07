package dev.syncforge.store.inmemory

import dev.syncforge.entity.EntityStore
import dev.syncforge.entity.SyncedEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory [EntityStore] for unit tests and early integration prototyping.
 *
 * Use with `@SyncForgeStore` and KSP-generated [dev.syncforge.entity.EntityStoreSyncHandler]
 * to exercise the non-Room store path without Room, Realm, or SQLDelight.
 */
class InMemoryEntityStore<T : SyncedEntity> : EntityStore<T> {

    private val mutex = Mutex()
    private val rows = linkedMapOf<String, T>()
    private var transactionDepth = 0

    override suspend fun findById(id: String): T? = withStoreLock { rows[id] }

    override suspend fun upsert(entity: T) {
        withStoreLock { rows[entity.id] = entity }
    }

    override suspend fun delete(id: String) {
        withStoreLock { rows.remove(id) }
    }

    override suspend fun <R> transaction(block: suspend () -> R): R {
        if (transactionDepth > 0) return block()
        return mutex.withLock {
            transactionDepth++
            try {
                block()
            } finally {
                transactionDepth--
            }
        }
    }

    /** Clears all rows — useful between test cases. */
    suspend fun clear() {
        withStoreLock { rows.clear() }
    }

    /** Returns an immutable snapshot of stored rows. */
    suspend fun snapshot(): Map<String, T> = withStoreLock { rows.toMap() }

    suspend fun size(): Int = withStoreLock { rows.size }

    private suspend inline fun <R> withStoreLock(crossinline block: () -> R): R =
        if (transactionDepth > 0) {
            block()
        } else {
            mutex.withLock { block() }
        }
}