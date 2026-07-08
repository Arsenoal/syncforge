package dev.syncforge.server

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySyncStoreTest {

    @Test
    fun pull_returnsPagesWhenLimitSet() {
        val store = InMemorySyncStore()
        repeat(3) { index ->
            store.push(
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
                nowMillis = 100L + index,
            )
        }

        val first = store.pull(0L, setOf("tasks"), 300L, limit = 2)
        assertEquals(2, first.deltas.size)
        assertTrue(first.hasMore)

        val second = store.pull(0L, setOf("tasks"), 400L, limit = 2, pageCursor = first.nextPageCursor)
        assertEquals(1, second.deltas.size)
        assertEquals(false, second.hasMore)
    }

    @Test
    fun pull_emptyTypes_returnsAllEntityTypes() {
        val store = InMemorySyncStore()
        store.push(
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
            nowMillis = 100L,
        )

        val response = store.pull(0L, emptySet(), 200L)
        assertEquals(2, response.deltas.size)
        assertEquals(setOf("tasks", "notes"), response.deltas.map { it.entityType }.toSet())
    }

    @Test
    fun push_rejectsUpdateOnTombstonedEntity() {
        val store = InMemorySyncStore()
        store.push(
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
            nowMillis = 100L,
        )
        assertTrue(store.forceDelete("tasks", "task-1", nowMillis = 200L))

        val response = store.push(
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
            nowMillis = 300L,
        )

        assertTrue(response.acknowledgedIds.isEmpty())
        assertEquals(1, response.rejected.size)
        assertEquals("CONFLICT", response.rejected.single().code)
    }

    @Test
    fun push_rejectsStaleUpdateAfterServerForceEdit() {
        val store = InMemorySyncStore()
        store.push(
            listOf(
                OutboxEntryDto(
                    id = 1,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"task-1","title":"Original"}""",
                    localVersion = 1,
                    createdAtMillis = 1,
                ),
            ),
            nowMillis = 100L,
        )
        assertEquals(
            200L,
            store.forceUpdate(
                entityType = "tasks",
                entityId = "task-1",
                payloadJson = """{"id":"task-1","title":"Original (server edit)"}""",
                nowMillis = 200L,
            ),
        )

        val response = store.push(
            listOf(
                OutboxEntryDto(
                    id = 2,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.UPDATE,
                    payloadJson = """{"id":"task-1","title":"Original","completed":true}""",
                    localVersion = 2,
                    createdAtMillis = 300L,
                ),
            ),
            nowMillis = 300L,
        )

        assertTrue(response.acknowledgedIds.isEmpty())
        assertEquals(1, response.rejected.size)
        assertEquals("CONFLICT", response.rejected.single().code)
    }

    @Test
    fun forceUpdate_usesPayloadUpdatedAtMillisForLwwDemos() {
        val store = InMemorySyncStore()
        store.push(
            listOf(
                OutboxEntryDto(
                    id = 1,
                    entityType = "tags",
                    entityId = "tag-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"tag-1","label":"Original","updatedAtMillis":100}""",
                    localVersion = 1,
                    createdAtMillis = 100L,
                ),
            ),
            nowMillis = 100L,
        )

        assertEquals(
            500L,
            store.forceUpdate(
                entityType = "tags",
                entityId = "tag-1",
                payloadJson = """{"id":"tag-1","label":"Remote","updatedAtMillis":500}""",
                nowMillis = 200L,
            ),
        )

        val pull = store.pull(
            sinceTimestampMillis = 0L,
            entityTypes = setOf("tags"),
            nowMillis = 600L,
            limit = 10,
            pageCursor = null,
        )
        assertEquals(500L, pull.deltas.single().updatedAtMillis)
    }

    @Test
    fun forceUpdate_bumpsRecordTimestampToHostNowWhenPayloadIsOlderThanPullCursor() {
        val store = InMemorySyncStore()
        store.push(
            listOf(
                OutboxEntryDto(
                    id = 1,
                    entityType = "tags",
                    entityId = "tag-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"id":"tag-1","label":"Original","updatedAtMillis":100}""",
                    localVersion = 1,
                    createdAtMillis = 100L,
                ),
            ),
            nowMillis = 100L,
        )

        assertEquals(
            500L,
            store.forceUpdate(
                entityType = "tags",
                entityId = "tag-1",
                payloadJson = """{"id":"tag-1","label":"Remote","updatedAtMillis":150}""",
                nowMillis = 500L,
            ),
        )

        val pull = store.pull(
            sinceTimestampMillis = 400L,
            entityTypes = setOf("tags"),
            nowMillis = 600L,
            limit = 10,
            pageCursor = null,
        )
        assertEquals(500L, pull.deltas.single().updatedAtMillis)
    }
}