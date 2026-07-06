package dev.syncforge.api

import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeBuilder
import dev.syncforge.compose.SyncStatusUiModel
import dev.syncforge.compose.toUiModel
import dev.syncforge.conflict.ConflictChoice
import dev.syncforge.conflict.ConflictPolicy
import dev.syncforge.conflict.ConflictStrategies
import dev.syncforge.conflict.conflictPolicy
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.SyncStatus
import dev.syncforge.sync.SyncManager
import dev.syncforge.sync.SyncWorkScheduler
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Guards APIs that graduated to stable at 1.0 (no [ExperimentalSyncForgeApi]).
 * Consumers can use these without module-wide opt-in.
 */
class StableApiSurfaceTest {

    @Test
    fun conflictPolicyAndStrategiesAreStable() {
        val policy = conflictPolicy {
            default(ConflictStrategies.lastWriteWins())
            entity("task") { deferToUser() }
        }
        assertEquals(ConflictStrategies.lastWriteWins()::class, policy.strategyFor("other")::class)
    }

    @Test
    fun conflictChoiceAndDefaultPolicyAreStable() {
        assertNotNull(ConflictPolicy.Default)
        assertEquals(ConflictChoice.KeepLocal, ConflictChoice.KeepLocal)
    }

    @Test
    fun syncStatusUiModelMappingIsStable() {
        val model: SyncStatusUiModel = SyncStatus.Idle.toUiModel()
        assertEquals("Up to date", model.label)
    }

    @Test
    fun syncWorkSchedulerContractIsStable() {
        val scheduler = object : SyncWorkScheduler {
            override fun schedulePeriodic(interval: kotlin.time.Duration) = Unit
            override fun scheduleRetry(delay: kotlin.time.Duration) = Unit
            override fun cancel() = Unit
        }
        scheduler.schedulePeriodic(15.minutes)
    }

    @Test
    fun syncManagerStableMembersHaveNoExperimentalAnnotation() {
        val stable = setOf(
            "getStatus",
            "getConflicts",
            "sync",
            "push",
            "pull",
            "enqueueChange",
            "schedulePeriodicSync",
            "cancelScheduledSync",
            "resolveConflict",
            "findOpenConflict",
        )
        for (method in SyncManager::class.java.methods) {
            if (method.name in stable) {
                assertNull(
                    method.getAnnotation(ExperimentalSyncForgeApi::class.java),
                    "Expected stable SyncManager member: ${method.name}",
                )
            }
        }
    }

    @Test
    fun lowLevelSyncForgeFactoryMethodsExist() {
        val factoryMethods = SyncForge::class.java.methods.filter {
            it.name in setOf("create", "createWithRetry") && !it.name.contains("$")
        }
        assertTrue(factoryMethods.size >= 2)
    }

    /** Compile-time check: core [SyncManager] members are stable (debug/conflictHistory excluded). */
    private suspend fun useStableSyncManagerContract(manager: SyncManager) {
        val status: StateFlow<SyncStatus> = manager.status
        val conflicts = manager.conflicts
        manager.schedulePeriodicSync()
        manager.cancelScheduledSync()
        manager.sync()
        manager.push()
        manager.pull()
        manager.enqueueChange(
            Change.delete<SyncedEntity>(
                entityType = "task",
                entityId = "1",
                localVersion = 0L,
                updatedAtMillis = 0L,
            ),
        )
        manager.resolveConflict("task", "1", ConflictChoice.KeepLocal)
        manager.findOpenConflict("task", "1")
        assertNotNull(status)
        assertNotNull(conflicts)
    }
}