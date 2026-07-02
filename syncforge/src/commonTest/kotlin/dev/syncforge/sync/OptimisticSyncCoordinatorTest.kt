package dev.syncforge.sync

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.ChangeType
import dev.syncforge.model.SyncState
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.test.FakeEntitySyncHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OptimisticSyncCoordinatorTest {

    private data class Task(
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
    ) : SyncedEntity

    @Test
    fun enqueue_capturesSnapshot_appliesOptimistic_andPersistsOutbox() = runTest {
        val handler = FakeEntitySyncHandler("tasks").apply {
            snapshotToReturn = """{"id":"t1"}"""
            serializedPayload = """{"id":"t1","title":"Buy milk"}"""
        }
        val outbox = InMemoryOutboxRepository()
        val coordinator = OptimisticSyncCoordinator(
            config = SyncConfig(entityTypes = setOf("tasks")),
            registry = EntityRegistry(listOf(handler)),
            outbox = outbox,
        )

        val change = Change.create("tasks", Task("t1", localVersion = 1, updatedAtMillis = 100L))
        coordinator.enqueue(change)

        assertEquals(1, handler.optimisticChanges.size)
        assertEquals(1, handler.snapshots.size)
        val entry = outbox.peek(nowMillis = Long.MAX_VALUE).single()
        assertEquals("""{"id":"t1"}""", entry.rollbackSnapshotJson)
        assertNotNull(entry.payloadJson)
    }

    @Test
    fun enqueue_withOptimisticDisabled_skipsRoomWrite() = runTest {
        val handler = FakeEntitySyncHandler("tasks")
        val outbox = InMemoryOutboxRepository()
        val coordinator = OptimisticSyncCoordinator(
            config = SyncConfig(
                entityTypes = setOf("tasks"),
                enableOptimisticUpdates = false,
            ),
            registry = EntityRegistry(listOf(handler)),
            outbox = outbox,
        )

        coordinator.enqueue(Change.create("tasks", Task("t1", 1, 100L)))

        assertTrue(handler.optimisticChanges.isEmpty())
        assertTrue(handler.snapshots.isEmpty())
        assertEquals(null, outbox.peek(nowMillis = Long.MAX_VALUE).single().rollbackSnapshotJson)
    }
}