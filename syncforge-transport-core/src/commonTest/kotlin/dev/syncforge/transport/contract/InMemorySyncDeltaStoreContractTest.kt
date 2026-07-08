package dev.syncforge.transport.contract

import dev.syncforge.transport.SyncDeltaStore
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class InMemorySyncDeltaStoreContractTest {

    private fun createContext(): ContractTestContext {
        var clockMillis = 0L
        val store = InMemorySyncDeltaStore(clock = { clockMillis })
        return ContractTestContext(store = store, setClock = { clockMillis = it })
    }

    @Test
    fun pull_returnsPagesWhenLimitSet() = runTest {
        val ctx = createContext()
        SyncDeltaStoreContract.pull_returnsPagesWhenLimitSet(ctx.store, ctx.setClock)
    }

    @Test
    fun pull_emptyTypes_returnsAllEntityTypes() = runTest {
        val ctx = createContext()
        SyncDeltaStoreContract.pull_emptyTypes_returnsAllEntityTypes(ctx.store, ctx.setClock)
    }

    @Test
    fun push_rejectsUpdateOnTombstonedEntity() = runTest {
        val ctx = createContext()
        SyncDeltaStoreContract.push_rejectsUpdateOnTombstonedEntity(
            store = ctx.store,
            setClock = ctx.setClock,
            tombstone = { store -> tombstone(store) },
        )
    }

    private suspend fun tombstone(store: SyncDeltaStore) {
        check((store as InMemorySyncDeltaStore).forceDelete("tasks", "task-1", 200L))
    }
}