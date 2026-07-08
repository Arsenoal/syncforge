package dev.syncforge.server

import dev.syncforge.network.contract.SyncPushPullContract
import org.junit.Test

abstract class SyncStoreContractTest {
    protected abstract fun createStore(): SyncStore

    @Test
    fun pull_returnsPagesWhenLimitSet() {
        val store = createStore()
        SyncPushPullContract.pull_returnsPagesWhenLimitSet(
            push = store::push,
            pull = { since, types, now, limit, cursor ->
                store.pull(since, types, now, limit = limit, pageCursor = cursor)
            },
        )
    }

    @Test
    fun pull_emptyTypes_returnsAllEntityTypes() {
        val store = createStore()
        SyncPushPullContract.pull_emptyTypes_returnsAllEntityTypes(
            push = store::push,
            pull = { since, types, now, limit, cursor ->
                store.pull(since, types, now, limit = limit, pageCursor = cursor)
            },
        )
    }

    @Test
    fun push_rejectsUpdateOnTombstonedEntity() {
        val store = createStore()
        SyncPushPullContract.push_rejectsUpdateOnTombstonedEntity(
            push = store::push,
            tombstone = { tombstoneEntity(store, "tasks", "task-1", nowMillis = 200L) },
        )
    }

    protected open fun tombstoneEntity(store: SyncStore, entityType: String, entityId: String, nowMillis: Long) {
        val inMemory = store as? InMemorySyncStore
            ?: error("Override tombstoneEntity for non-InMemorySyncStore tests")
        check(inMemory.forceDelete(entityType, entityId, nowMillis))
    }
}