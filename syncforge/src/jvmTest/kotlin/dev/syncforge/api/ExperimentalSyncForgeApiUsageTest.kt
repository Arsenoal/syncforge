package dev.syncforge.api

import dev.syncforge.SyncForgeBuilder
import dev.syncforge.sync.SyncManager
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Compile-time guard: experimental [SyncManager] surfaces require explicit opt-in.
 */
@OptIn(ExperimentalSyncForgeApi::class)
class ExperimentalSyncForgeApiUsageTest {

    @Test
    fun experimentalSyncManagerSurfacesCompileWithOptIn() {
        val readExperimental: (SyncManager) -> Unit = { manager ->
            manager.debug
            manager.authState
            manager.conflictHistory
        }
        assertNotNull(readExperimental)
    }

    @Test
    fun experimentalBuilderCompilesWithOptIn() {
        val factory: () -> SyncForgeBuilder = { SyncForgeBuilder() }
        assertNotNull(factory)
    }
}