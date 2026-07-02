package dev.syncforge.sync

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.ChangeType
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncState
import dev.syncforge.model.SyncStatus
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.NoOpSyncTransport
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.test.FakeEntitySyncHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerImplTest {

    private data class StubEntity(
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
    ) : SyncedEntity

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val handler = FakeEntitySyncHandler("tasks")
    private val registry = EntityRegistry(listOf(handler))

    @Test
    fun sync_emptyOutbox_returnsSuccess() = runTest(dispatcher) {
        val manager = createManager()
        val result = manager.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(0, (result as SyncResult.Success).pushed)
    }

    @Test
    fun enqueueChange_thenPush_pushesEntryAndAcknowledges() = runTest(dispatcher) {
        val outbox = InMemoryOutboxRepository()
        val manager = createManager(outbox)

        manager.enqueueChange(
            Change(
                entityType = "tasks",
                entityId = "t1",
                type = ChangeType.CREATE,
                payload = StubEntity("t1", localVersion = 1, updatedAtMillis = 100L),
                localVersion = 1,
                updatedAtMillis = 100L,
            ),
        )

        val result = manager.push()
        advanceUntilIdle()
        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).pushed)
        assertEquals(listOf("t1"), handler.acknowledgedIds)
        assertEquals(SyncStatus.Idle, manager.status.value)
    }

    private fun createManager(
        outbox: InMemoryOutboxRepository = InMemoryOutboxRepository().apply {
            setMaxRetriesForObservation(maxRetries = 5)
        },
    ): SyncManagerImpl =
        SyncManagerImpl(
            config = SyncConfig(entityTypes = setOf("tasks")),
            outbox = outbox,
            transport = NoOpSyncTransport,
            registry = registry,
            networkMonitor = QuietNetworkMonitor,
            scope = scope,
        )

    private object QuietNetworkMonitor : NetworkMonitor {
        override val isOnline: Boolean = true
        override fun observeOnline() = emptyFlow<Boolean>()
    }
}