package dev.syncforge.api

import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeBuilder
import dev.syncforge.auth.AuthResult
import dev.syncforge.auth.AuthState
import dev.syncforge.compose.SyncStatusUiModel
import dev.syncforge.compose.toUiModel
import dev.syncforge.conflict.ConflictChoice
import dev.syncforge.conflict.ConflictEntityBuilder
import dev.syncforge.conflict.ConflictPolicy
import dev.syncforge.conflict.ConflictStrategies
import dev.syncforge.conflict.ConflictStrategyKind
import dev.syncforge.conflict.ThreeWayMergeResult
import dev.syncforge.conflict.conflictPolicy
import kotlinx.serialization.Serializable
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
import dev.syncforge.model.SyncStatus
import kotlinx.serialization.serializer
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
    fun gitLikeAndCrdtConflictBuildersAreStable() {
        val policy = conflictPolicy {
            entity("tasks") {
                gitLike<StableConflictTask> {
                    threeWayMerge { _, local, _ -> ThreeWayMergeResult.Merged(local) }
                }
            }
            entity("tags") {
                crdt(serializer<StableConflictTags>()) {
                    field("labels") { orSet() }
                }
            }
        }
        assertNotNull(policy.strategyFor("tasks"))
        assertNotNull(policy.strategyFor("tags"))
    }

    @Test
    fun conflictEntityBuilderGitLikeAndCrdtHaveNoExperimentalAnnotation() {
        val gitLikeMethod = ConflictEntityBuilder::class.java.methods.single { it.name == "gitLike" }
        val crdtMethod = ConflictEntityBuilder::class.java.methods.single {
            it.name == "crdt" && it.parameterCount == 2
        }
        assertNull(gitLikeMethod.getAnnotation(ExperimentalSyncForgeApi::class.java))
        assertNull(crdtMethod.getAnnotation(ExperimentalSyncForgeApi::class.java))
    }

    @Test
    fun conflictStrategyKindCatalogIsStable() {
        val policy = conflictPolicy {
            entity("notes") { strategy(ConflictStrategies.acceptRemote) }
            default(ConflictStrategyKind.LAST_WRITE_WINS)
        }
        assertEquals(
            ConflictStrategyKind.ACCEPT_REMOTE,
            ConflictStrategies.kindOf(policy.strategyFor("notes")),
        )
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
            "getAuthState",
            "getSession",
            "getConflicts",
            "register",
            "login",
            "logout",
            "sync",
            "push",
            "pull",
            "enqueueChange",
            "schedulePeriodicSync",
            "cancelScheduledSync",
            "resolveConflict",
            "updateConflictPolicy",
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
        val authState: StateFlow<AuthState> = manager.authState
        val session = manager.session
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
        manager.updateConflictPolicy(ConflictPolicy.Default)
        manager.findOpenConflict("task", "1")
        val registerResult: AuthResult = manager.register(mapOf("email" to "a@b.c", "password" to "x"))
        val loginResult: AuthResult = manager.login("a@b.c", charArrayOf('x'))
        val logoutResult: AuthResult = manager.logout()
        assertNotNull(status)
        assertNotNull(authState)
        assertNotNull(conflicts)
        assertNotNull(registerResult)
        assertNotNull(loginResult)
        assertNotNull(logoutResult)
        assertNotNull(session)
    }

    @Serializable
    private data class StableConflictTask(
        override val id: String,
        override val localVersion: Long = 0L,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.SYNCED,
        val title: String,
    ) : SyncedEntity

    @Serializable
    private data class StableConflictTags(
        override val id: String,
        override val localVersion: Long = 0L,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.SYNCED,
        val labels: List<String> = emptyList(),
    ) : SyncedEntity
}