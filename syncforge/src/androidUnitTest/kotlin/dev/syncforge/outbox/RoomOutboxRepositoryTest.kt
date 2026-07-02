package dev.syncforge.outbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomOutboxRepositoryTest {

    private lateinit var database: SyncForgeDatabase
    private lateinit var repository: RoomOutboxRepository

    private data class Task(
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
    ) : SyncedEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SyncForgeDatabaseFactory.createInMemory(context)
        repository = RoomOutboxRepository(database.outboxDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun enqueueAndPeek_persistsEntryWithSnapshot() = runTest {
        val change = Change.create("tasks", Task("t1", localVersion = 1, updatedAtMillis = 100L))
        repository.enqueue(
            change = change,
            payloadJson = """{"id":"t1"}""",
            rollbackSnapshotJson = """{"id":"t1","old":true}""",
        )

        assertEquals(1, repository.countPending())
        val entry = repository.peek(nowMillis = Long.MAX_VALUE).single()
        assertEquals("tasks", entry.entityType)
        assertEquals("""{"id":"t1","old":true}""", entry.rollbackSnapshotJson)
    }

    @Test
    fun markAcknowledged_removesEntry() = runTest {
        val entry = repository.enqueue(
            change = Change.create("tasks", Task("t1", 1, 100L)),
            payloadJson = "{}",
        )
        repository.markAcknowledged(listOf(entry.id))
        assertEquals(0, repository.countPending())
    }
}