package dev.syncforge.mockserver

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySyncStoreJvmTest {

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
}