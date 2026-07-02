package dev.syncforge.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.syncforge.conflict.ConflictResolutionKind
import dev.syncforge.conflict.ConflictStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SqlDelightConflictStoreTest {

    @Test
    fun recordDeferred_persistsOpenConflict() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SyncForgePersistenceDatabase.Schema.create(driver)
        val store = SyncForgePersistence.create(driver).conflictStore()

        val id = store.recordDeferred(
            entityType = "tasks",
            entityId = "task-1",
            localJson = """{"id":"task-1","title":"local"}""",
            remoteJson = """{"id":"task-1","title":"remote"}""",
            localUpdatedAtMillis = 100L,
            remoteServerVersion = 2L,
            remoteUpdatedAtMillis = 200L,
            detectedAtMillis = 300L,
        )

        assertEquals(1L, id)
        assertEquals(1, store.countOpen())

        val open = store.findOpen("tasks", "task-1")
        assertNotNull(open)
        assertEquals(ConflictStatus.OPEN, open.status)
    }

    @Test
    fun markUserResolved_closesConflict() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SyncForgePersistenceDatabase.Schema.create(driver)
        val store = SyncForgePersistence.create(driver).conflictStore()

        val id = store.recordDeferred(
            entityType = "tasks",
            entityId = "task-2",
            localJson = "{}",
            remoteJson = "{}",
            localUpdatedAtMillis = 1L,
            remoteServerVersion = 1L,
            remoteUpdatedAtMillis = 2L,
            detectedAtMillis = 3L,
        )

        store.markUserResolved(id, ConflictResolutionKind.KEEP_LOCAL)

        assertEquals(0, store.countOpen())
        val record = store.findOpen("tasks", "task-2")
        assertEquals(null, record)
    }
}