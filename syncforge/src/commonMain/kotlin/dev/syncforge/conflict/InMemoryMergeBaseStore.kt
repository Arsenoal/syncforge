package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [MergeBaseStore] for tests. Does not survive process death.
 */
@ExperimentalSyncForgeApi
class InMemoryMergeBaseStore : MergeBaseStore {

    private val mutex = Mutex()
    private val snapshots = mutableMapOf<Pair<String, String>, MergeBaseSnapshot>()

    override suspend fun get(entityType: String, entityId: String): MergeBaseSnapshot? =
        mutex.withLock { snapshots[entityType to entityId] }

    override suspend fun put(snapshot: MergeBaseSnapshot) = mutex.withLock {
        snapshots[snapshot.entityType to snapshot.entityId] = snapshot
    }

    override suspend fun remove(entityType: String, entityId: String) {
        mutex.withLock {
            snapshots.remove(entityType to entityId)
        }
    }
}