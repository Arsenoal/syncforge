package dev.syncforge.store.inmemory

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.EntityStoreSyncHandler
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalSyncForgeApi::class)
class InMemoryEntityStoreTest {

    @Serializable
    private data class Task(
        val title: String = "",
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    @Test
    fun upsertAndFind_roundTrip() = runTest {
        val store = InMemoryEntityStore<Task>()
        val task = Task(id = "t1", title = "A", localVersion = 1, updatedAtMillis = 10)

        store.upsert(task)
        assertEquals("A", store.findById("t1")?.title)
    }

    @Test
    fun delete_removesRow() = runTest {
        val store = InMemoryEntityStore<Task>()
        store.upsert(Task(id = "t1", title = "A", localVersion = 1, updatedAtMillis = 10))

        store.delete("t1")
        assertNull(store.findById("t1"))
    }

    @Test
    fun clear_emptiesStore() = runTest {
        val store = InMemoryEntityStore<Task>()
        store.upsert(Task(id = "t1", title = "A", localVersion = 1, updatedAtMillis = 10))

        store.clear()
        assertEquals(0, store.size())
        assertNull(store.findById("t1"))
    }

    @Test
    fun transaction_persistsUpsert() = runTest {
        val store = InMemoryEntityStore<Task>()
        store.transaction {
            store.upsert(Task(id = "t1", title = "inside", localVersion = 1, updatedAtMillis = 1))
        }
        assertEquals("inside", store.findById("t1")?.title)
    }

    @Test
    fun concurrentUpserts_remainConsistent() = runTest {
        val store = InMemoryEntityStore<Task>()
        (1..20).map { index ->
            async {
                store.upsert(
                    Task(
                        id = "t$index",
                        title = "task-$index",
                        localVersion = index.toLong(),
                        updatedAtMillis = index.toLong(),
                    ),
                )
            }
        }.awaitAll()

        assertEquals(20, store.size())
        assertEquals("task-7", store.findById("t7")?.title)
    }

    @Test
    fun entityStoreSyncHandler_nonRoomPath() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val store = InMemoryEntityStore<Task>()
        val handler = object : EntityStoreSyncHandler<Task>(store) {
            override val entityType: String = "tasks"
            override fun toJson(entity: Task): String = json.encodeToString(entity)
            override fun fromJson(jsonString: String): Task = json.decodeFromString(jsonString)
            override fun withSyncState(entity: Task, state: SyncState): Task = entity.copy(syncState = state)
        }

        val task = Task(id = "t1", title = "Buy milk", localVersion = 1, updatedAtMillis = 100)
        handler.applyOptimistic(Change.create("tasks", task))

        assertEquals(SyncState.PENDING, store.findById("t1")?.syncState)
        assertEquals("Buy milk", store.findById("t1")?.title)
    }
}