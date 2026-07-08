package dev.syncforge.persistence

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import dev.syncforge.conflict.MergeBaseSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightMergeBaseStoreTest {

    @Test
    fun putAndGet_roundTripsSnapshot() = runTest {
        val store = createStore()

        store.put(
            MergeBaseSnapshot(
                entityType = "tasks",
                entityId = "task-1",
                payloadJson = """{"id":"task-1","title":"synced"}""",
                serverVersion = 5L,
                updatedAtMillis = 100L,
                storedAtMillis = 200L,
            ),
        )

        val loaded = store.get("tasks", "task-1")
        assertEquals("task-1", loaded?.entityId)
        assertEquals(5L, loaded?.serverVersion)
        assertEquals("""{"id":"task-1","title":"synced"}""", loaded?.payloadJson)
    }

    @Test
    fun put_overwritesExistingSnapshot() = runTest {
        val store = createStore()
        val key = MergeBaseSnapshot(
            entityType = "notes",
            entityId = "note-1",
            payloadJson = """{"id":"note-1","v":1}""",
            serverVersion = 1L,
            updatedAtMillis = 10L,
            storedAtMillis = 20L,
        )
        store.put(key)
        store.put(
            key.copy(
                payloadJson = """{"id":"note-1","v":2}""",
                serverVersion = 2L,
                updatedAtMillis = 30L,
                storedAtMillis = 40L,
            ),
        )

        val loaded = store.get("notes", "note-1")
        assertEquals(2L, loaded?.serverVersion)
        assertEquals("""{"id":"note-1","v":2}""", loaded?.payloadJson)
    }

    @Test
    fun remove_deletesSnapshot() = runTest {
        val store = createStore()
        store.put(
            MergeBaseSnapshot(
                entityType = "tasks",
                entityId = "task-2",
                payloadJson = "{}",
                serverVersion = null,
                updatedAtMillis = 1L,
                storedAtMillis = 2L,
            ),
        )

        store.remove("tasks", "task-2")

        assertNull(store.get("tasks", "task-2"))
    }

    private fun createStore(): dev.syncforge.conflict.MergeBaseStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        runBlocking { SyncForgePersistenceDatabase.Schema.awaitCreate(driver) }
        return SyncForgePersistence.create(driver).mergeBaseStore()
    }
}