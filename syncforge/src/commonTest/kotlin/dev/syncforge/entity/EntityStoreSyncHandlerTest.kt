package dev.syncforge.entity

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.Change
import dev.syncforge.model.ChangeType
import dev.syncforge.model.SyncState
import dev.syncforge.sync.OptimisticSyncCoordinator
import dev.syncforge.sync.SyncConfig
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.store.inmemory.InMemoryEntityStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalSyncForgeApi::class)
class EntityStoreSyncHandlerTest {

    @Serializable
    private data class Task(
        val title: String = "",
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private class TaskStoreHandler(
        store: EntityStore<Task>,
        private val json: Json,
    ) : EntityStoreSyncHandler<Task>(store) {
        override val entityType: String = "tasks"
        override fun toJson(entity: Task): String = json.encodeToString(entity)
        override fun fromJson(jsonString: String): Task = json.decodeFromString(jsonString)
        override fun withSyncState(entity: Task, state: SyncState): Task = entity.copy(syncState = state)
    }

    @Test
    fun store_crudDelegatesToEntityStore() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val store = InMemoryEntityStore<Task>()
        val handler = TaskStoreHandler(store, json)
        val task = Task(id = "t1", title = "A", localVersion = 1, updatedAtMillis = 10)

        handler.applyOptimistic(Change.create("tasks", task))
        assertEquals(SyncState.PENDING, store.findById("t1")?.syncState)

        val updated = task.copy(title = "B", localVersion = 2, syncState = SyncState.PENDING)
        handler.applyOptimistic(Change.update("tasks", updated))
        assertEquals("B", store.findById("t1")?.title)

        handler.applyOptimistic(Change.delete<Task>("tasks", "t1", localVersion = 3, updatedAtMillis = 11))
        assertNull(store.findById("t1"))
    }

    @Test
    fun handler_enqueue_appliesOptimisticThroughStore() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val store = InMemoryEntityStore<Task>()
        val handler = TaskStoreHandler(store, json)
        val outbox = InMemoryOutboxRepository()
        val coordinator = OptimisticSyncCoordinator(
            config = SyncConfig(entityTypes = setOf("tasks")),
            registry = EntityRegistry.of(handler),
            outbox = outbox,
        )

        val task = Task(id = "t1", title = "Buy milk", localVersion = 1, updatedAtMillis = 100)
        coordinator.enqueue(Change.create("tasks", task))

        val persisted = store.findById("t1")
        assertEquals(SyncState.PENDING, persisted?.syncState)
        assertEquals("Buy milk", persisted?.title)
    }

    @Test
    fun store_transaction_persistsUpsert() = runTest {
        val store = InMemoryEntityStore<Task>()
        store.transaction {
            store.upsert(Task(id = "t1", localVersion = 1, updatedAtMillis = 1))
        }
        assertEquals("t1", store.findById("t1")?.id)
    }
}