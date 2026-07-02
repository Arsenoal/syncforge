package dev.syncforge

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.network.NoOpSyncTransport
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.test.FakeEntitySyncHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class SyncForgeBuilderTest {

    private val dispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(dispatcher)
    private val taskHandler = FakeEntitySyncHandler("tasks")

    @Test
    fun build_derivesEntityTypesFromHandlers() = runTest(dispatcher) {
        val manager = SyncForge.builder {
            handler(taskHandler)
            outbox = InMemoryOutboxRepository()
            transport = NoOpSyncTransport
            scope = appScope
            enableRetry = false
        }

        manager.enqueueChange(
            dev.syncforge.model.Change.create("tasks", TestEntity("1")),
        )
    }

    @Test
    fun build_acceptsPrebuiltRegistry() {
        val manager = SyncForge.builder {
            registry(EntityRegistry.of(taskHandler))
            outbox = InMemoryOutboxRepository()
            transport = NoOpSyncTransport
            scope = appScope
            enableRetry = false
        }

        assertEquals(dev.syncforge.model.SyncStatus.Idle, manager.status.value)
    }

    @Test
    fun build_requiresOutboxTransportAndScope() {
        assertFailsWith<IllegalArgumentException> {
            SyncForge.builder {
                handler(taskHandler)
            }
        }
    }

    private data class TestEntity(
        override val id: String,
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 0,
        override val syncState: dev.syncforge.model.SyncState = dev.syncforge.model.SyncState.PENDING,
    ) : dev.syncforge.entity.SyncedEntity
}