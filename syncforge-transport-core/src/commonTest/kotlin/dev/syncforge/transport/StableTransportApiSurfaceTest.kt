package dev.syncforge.transport

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.transport.contract.InMemorySyncDeltaStore
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Guards transport APIs graduated to stable at 2.0.1 (no [ExperimentalSyncForgeApi]).
 */
class StableTransportApiSurfaceTest {

    @Test
    fun syncDeltaStoreAndAdapterHaveNoExperimentalAnnotation() {
        assertNull(SyncDeltaStore::class.java.getAnnotation(ExperimentalSyncForgeApi::class.java))
        assertNull(DeltaStoreSyncTransport::class.java.getAnnotation(ExperimentalSyncForgeApi::class.java))
        assertNull(InMemorySyncDeltaStore::class.java.getAnnotation(ExperimentalSyncForgeApi::class.java))
    }
}