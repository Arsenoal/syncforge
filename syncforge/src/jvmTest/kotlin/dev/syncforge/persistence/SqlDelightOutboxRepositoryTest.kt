package dev.syncforge.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.ChangeType
import dev.syncforge.model.SyncState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightOutboxRepositoryTest {

    @Test
    fun enqueue_persistsAndPeekReturnsEntry() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SyncForgePersistenceDatabase.Schema.create(driver)
        val repository = SyncForgePersistence.create(driver).outboxRepository(maxRetries = 3)

        val task = TestEntity(id = "task-1", localVersion = 1, updatedAtMillis = 1_000L)
        val entry = repository.enqueue(
            change = Change.create("tasks", task),
            payloadJson = """{"id":"task-1"}""",
            rollbackSnapshotJson = null,
        )

        assertTrue(entry.id > 0)
        assertEquals("tasks", entry.entityType)
        assertEquals(ChangeType.CREATE, entry.changeType)

        val batch = repository.peek(limit = 10, nowMillis = 2_000L)
        assertEquals(1, batch.size)
        assertEquals(entry.id, batch.first().id)
    }

    @Test
    fun markAcknowledged_removesFromPeek() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SyncForgePersistenceDatabase.Schema.create(driver)
        val repository = SyncForgePersistence.create(driver).outboxRepository()

        val task = TestEntity(id = "task-2", localVersion = 2, updatedAtMillis = 2_000L)
        val entry = repository.enqueue(
            change = Change.update("tasks", task),
            payloadJson = """{"id":"task-2"}""",
        )

        repository.markAcknowledged(listOf(entry.id))

        assertEquals(0, repository.peek(limit = 10, nowMillis = 3_000L).size)
    }

    @Test
    fun markFailed_incrementsRetryCount() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SyncForgePersistenceDatabase.Schema.create(driver)
        val repository = SyncForgePersistence.create(driver).outboxRepository(maxRetries = 3)

        val task = TestEntity(id = "task-3", localVersion = 1, updatedAtMillis = 1_000L)
        val entry = repository.enqueue(
            change = Change.create("tasks", task),
            payloadJson = "{}",
        )

        repository.markFailed(entry.id, error = "network", retryable = true, maxRetries = 3)

        val failed = repository.peek(limit = 10, nowMillis = 0L)
        assertEquals(0, failed.size)

        val ready = repository.peek(limit = 10, nowMillis = Long.MAX_VALUE)
        assertEquals(1, ready.size)
        assertEquals(1, ready.first().retryCount)
    }

    private data class TestEntity(
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
    ) : SyncedEntity
}