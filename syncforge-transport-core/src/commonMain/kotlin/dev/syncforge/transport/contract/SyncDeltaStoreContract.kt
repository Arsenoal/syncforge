package dev.syncforge.transport.contract

import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.transport.SyncDeltaStore

/**
 * Shared [SyncDeltaStore] contract scenarios (1.4-07). Aligns with
 * [dev.syncforge.network.contract.SyncPushPullContract] for REST backends.
 */
object SyncDeltaStoreContract {

    suspend fun pull_returnsPagesWhenLimitSet(
        store: SyncDeltaStore,
        setClock: (Long) -> Unit,
    ) {
        repeat(3) { index ->
            setClock(100L + index)
            store.appendEntries(
                listOf(
                    OutboxEntry(
                        id = index.toLong(),
                        entityType = "tasks",
                        entityId = "task-$index",
                        changeType = ChangeType.CREATE,
                        payloadJson = """{"id":"task-$index"}""",
                        localVersion = 1L,
                        createdAtMillis = index.toLong(),
                    ),
                ),
            )
        }

        setClock(300L)
        val first = store.queryDeltas(0L, setOf("tasks"), pageSize = 2, pageCursor = null)
        require(first.deltas.size == 2) { "Expected 2 deltas, got ${first.deltas.size}" }
        require(first.hasMore) { "Expected hasMore=true on first page" }

        setClock(400L)
        val second = store.queryDeltas(0L, setOf("tasks"), pageSize = 2, pageCursor = first.nextPageCursor)
        require(second.deltas.size == 1) { "Expected 1 delta, got ${second.deltas.size}" }
        require(!second.hasMore) { "Expected hasMore=false on last page" }
    }

    suspend fun pull_emptyTypes_returnsAllEntityTypes(
        store: SyncDeltaStore,
        setClock: (Long) -> Unit,
    ) {
        setClock(100L)
        store.appendEntries(
            listOf(
                OutboxEntry(
                    id = 1L,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"task-1"}""",
                    localVersion = 1L,
                    createdAtMillis = 1L,
                ),
                OutboxEntry(
                    id = 2L,
                    entityType = "notes",
                    entityId = "note-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"note-1"}""",
                    localVersion = 1L,
                    createdAtMillis = 2L,
                ),
            ),
        )

        setClock(200L)
        val response = store.queryDeltas(0L, emptySet(), pageSize = Int.MAX_VALUE, pageCursor = null)
        require(response.deltas.size == 2) { "Expected 2 deltas, got ${response.deltas.size}" }
        val types = response.deltas.map { it.entityType }.toSet()
        require(types == setOf("tasks", "notes")) { "Expected tasks+notes, got $types" }
    }

    suspend fun push_rejectsUpdateOnTombstonedEntity(
        store: SyncDeltaStore,
        setClock: (Long) -> Unit,
        tombstone: suspend (SyncDeltaStore) -> Unit,
    ) {
        setClock(100L)
        store.appendEntries(
            listOf(
                OutboxEntry(
                    id = 1L,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"task-1","title":"Task"}""",
                    localVersion = 1L,
                    createdAtMillis = 1L,
                ),
            ),
        )
        setClock(200L)
        tombstone(store)

        setClock(300L)
        val response = store.appendEntries(
            listOf(
                OutboxEntry(
                    id = 2L,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.UPDATE,
                    payloadJson = """{"id":"task-1","title":"Task","completed":true}""",
                    localVersion = 2L,
                    createdAtMillis = 300L,
                ),
            ),
        )

        require(response.acknowledgedIds.isEmpty()) {
            "Expected no acknowledgements, got ${response.acknowledgedIds}"
        }
        require(response.rejected.size == 1) { "Expected 1 rejection, got ${response.rejected.size}" }
        require(response.rejected.single().error.code == SyncError.Code.CONFLICT) {
            "Expected CONFLICT, got ${response.rejected.single().error.code}"
        }
    }
}