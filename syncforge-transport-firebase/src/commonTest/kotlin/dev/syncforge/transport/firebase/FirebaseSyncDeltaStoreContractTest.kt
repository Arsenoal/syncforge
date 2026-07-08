package dev.syncforge.transport.firebase

import dev.syncforge.model.OutboxEntry
import dev.syncforge.transport.contract.ContractSyncApi
import dev.syncforge.transport.contract.ContractTestContext
import dev.syncforge.transport.contract.InMemorySyncBackend
import dev.syncforge.transport.contract.SyncDeltaStoreContract
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class FirebaseSyncDeltaStoreContractTest {

    private fun createContext(backend: InMemorySyncBackend = InMemorySyncBackend()): Pair<ContractTestContext, InMemorySyncBackend> {
        val contractApi = ContractSyncApi(backend)
        var clockMillis = 0L
        val store = FirebaseSyncDeltaStore(
            api = object : FirebaseSyncApi {
                override suspend fun push(entries: List<OutboxEntry>, nowMillis: Long) =
                    contractApi.push(entries, nowMillis)

                override suspend fun pull(
                    sinceTimestampMillis: Long,
                    entityTypes: Set<String>,
                    pageSize: Int,
                    pageCursor: String?,
                    nowMillis: Long,
                ) = contractApi.pull(sinceTimestampMillis, entityTypes, pageSize, pageCursor, nowMillis)
            },
            clock = { clockMillis },
        )
        return ContractTestContext(store = store, setClock = { clockMillis = it }) to backend
    }

    @Test
    fun pull_returnsPagesWhenLimitSet() = runTest {
        val (ctx, _) = createContext()
        SyncDeltaStoreContract.pull_returnsPagesWhenLimitSet(ctx.store, ctx.setClock)
    }

    @Test
    fun pull_emptyTypes_returnsAllEntityTypes() = runTest {
        val (ctx, _) = createContext()
        SyncDeltaStoreContract.pull_emptyTypes_returnsAllEntityTypes(ctx.store, ctx.setClock)
    }

    @Test
    fun push_rejectsUpdateOnTombstonedEntity() = runTest {
        val backend = InMemorySyncBackend()
        val (ctx, _) = createContext(backend)
        SyncDeltaStoreContract.push_rejectsUpdateOnTombstonedEntity(
            store = ctx.store,
            setClock = ctx.setClock,
            tombstone = { check(backend.forceDelete("tasks", "task-1", 200L)) },
        )
    }
}