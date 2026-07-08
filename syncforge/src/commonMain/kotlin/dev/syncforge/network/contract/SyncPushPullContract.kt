package dev.syncforge.network.contract

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse

/**
 * Shared push/pull semantics for REST [dev.syncforge.server.SyncStore] and
 * client [dev.syncforge.transport.SyncDeltaStore] implementations (1.4-07).
 *
 * Backends and transports run these scenarios in unit tests to stay aligned with
 * [docs/REST_API.md](../../../../../../docs/REST_API.md).
 */
object SyncPushPullContract {

    fun pull_returnsPagesWhenLimitSet(
        push: (List<OutboxEntryDto>, Long) -> PushResponse,
        pull: (Long, Set<String>, Long, Int, String?) -> PullResponse,
    ) {
        repeat(3) { index ->
            push(
                listOf(
                    OutboxEntryDto(
                        id = index.toLong(),
                        entityType = "tasks",
                        entityId = "task-$index",
                        changeType = ChangeType.CREATE,
                        payloadJson = """{"id":"task-$index"}""",
                        localVersion = 1,
                        createdAtMillis = index.toLong(),
                    ),
                ),
                100L + index,
            )
        }

        val first = pull(0L, setOf("tasks"), 300L, 2, null)
        require(first.deltas.size == 2) { "Expected 2 deltas, got ${first.deltas.size}" }
        require(first.hasMore) { "Expected hasMore=true on first page" }

        val second = pull(0L, setOf("tasks"), 400L, 2, first.nextPageCursor)
        require(second.deltas.size == 1) { "Expected 1 delta, got ${second.deltas.size}" }
        require(!second.hasMore) { "Expected hasMore=false on last page" }
    }

    fun pull_emptyTypes_returnsAllEntityTypes(
        push: (List<OutboxEntryDto>, Long) -> PushResponse,
        pull: (Long, Set<String>, Long, Int, String?) -> PullResponse,
    ) {
        push(
            listOf(
                OutboxEntryDto(
                    id = 1,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"task-1"}""",
                    localVersion = 1,
                    createdAtMillis = 1,
                ),
                OutboxEntryDto(
                    id = 2,
                    entityType = "notes",
                    entityId = "note-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"note-1"}""",
                    localVersion = 1,
                    createdAtMillis = 2,
                ),
            ),
            100L,
        )

        val response = pull(0L, emptySet(), 200L, Int.MAX_VALUE, null)
        require(response.deltas.size == 2) { "Expected 2 deltas, got ${response.deltas.size}" }
        val types = response.deltas.map { it.entityType }.toSet()
        require(types == setOf("tasks", "notes")) { "Expected tasks+notes, got $types" }
    }

    fun push_rejectsUpdateOnTombstonedEntity(
        push: (List<OutboxEntryDto>, Long) -> PushResponse,
        tombstone: () -> Unit,
    ) {
        push(
            listOf(
                OutboxEntryDto(
                    id = 1,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"task-1","title":"Task"}""",
                    localVersion = 1,
                    createdAtMillis = 1,
                ),
            ),
            100L,
        )
        tombstone()

        val response = push(
            listOf(
                OutboxEntryDto(
                    id = 2,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.UPDATE,
                    payloadJson = """{"id":"task-1","title":"Task","completed":true}""",
                    localVersion = 2,
                    createdAtMillis = 300L,
                ),
            ),
            300L,
        )

        require(response.acknowledgedIds.isEmpty()) {
            "Expected no acknowledgements, got ${response.acknowledgedIds}"
        }
        require(response.rejected.size == 1) { "Expected 1 rejection, got ${response.rejected.size}" }
        require(response.rejected.single().code == "CONFLICT") {
            "Expected CONFLICT, got ${response.rejected.single().code}"
        }
    }
}