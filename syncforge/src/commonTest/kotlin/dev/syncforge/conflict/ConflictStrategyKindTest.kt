package dev.syncforge.conflict

import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ConflictStrategyKindTest {

    private data class Task(
        override val id: String,
        val title: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
    ) : SyncedEntity

    @Test
    fun fromKind_resolvesSimpleCatalogKinds() {
        assertEquals(
            ConflictStrategyKind.LAST_WRITE_WINS,
            ConflictStrategies.kindOf(ConflictStrategies.fromKind(ConflictStrategyKind.LAST_WRITE_WINS)),
        )
        assertEquals(
            ConflictStrategyKind.ACCEPT_LOCAL,
            ConflictStrategies.kindOf(ConflictStrategies.fromKind(ConflictStrategyKind.ACCEPT_LOCAL)),
        )
        assertEquals(
            ConflictStrategyKind.ACCEPT_REMOTE,
            ConflictStrategies.kindOf(ConflictStrategies.fromKind(ConflictStrategyKind.ACCEPT_REMOTE)),
        )
        assertEquals(
            ConflictStrategyKind.DEFER_TO_USER,
            ConflictStrategies.kindOf(ConflictStrategies.fromKind(ConflictStrategyKind.DEFER_TO_USER)),
        )
    }

    @Test
    fun fromKind_matchesDirectFactoryBehavior() {
        runBlocking {
        val local = Task("1", "local", localVersion = 2, updatedAtMillis = 100)
        val remote = Task("1", "remote", localVersion = 3, updatedAtMillis = 200)
        val context = ConflictContext(
            entityType = "tasks",
            local = local,
            remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200),
            remotePayload = remote,
        )

        val fromCatalog = ConflictStrategies.fromKind(ConflictStrategyKind.ACCEPT_REMOTE)
        val direct = ConflictStrategies.alwaysRemote()
        val catalogOutcome = fromCatalog.resolve(context)
        val directOutcome = direct.resolve(context)

        assertIs<ConflictOutcome.Resolved<Task>>(catalogOutcome)
        assertIs<ConflictOutcome.Resolved<Task>>(directOutcome)
        assertEquals(
            (directOutcome as ConflictOutcome.Resolved).resolution,
            (catalogOutcome as ConflictOutcome.Resolved).resolution,
        )
        }
    }

    @Test
    fun fromKind_rejectsConfiguredKinds() {
        assertFailsWith<IllegalArgumentException> {
            ConflictStrategies.fromKind(ConflictStrategyKind.MERGE)
        }
        assertFailsWith<IllegalArgumentException> {
            ConflictStrategies.fromKind(ConflictStrategyKind.GIT_LIKE)
        }
        assertFailsWith<IllegalArgumentException> {
            ConflictStrategies.fromKind(ConflictStrategyKind.CRDT)
        }
    }

    @Test
    fun policy_entityStrategyFromKind() {
        runBlocking {
        val policy = conflictPolicy {
            entity("notes") { strategy(ConflictStrategies.acceptRemote) }
            entity("tasks") { strategy(ConflictStrategyKind.DEFER_TO_USER) }
        }

        val notesStrategy = policy.strategyFor("notes")
        val tasksStrategy = policy.strategyFor("tasks")
        assertEquals(ConflictStrategyKind.ACCEPT_REMOTE, ConflictStrategies.kindOf(notesStrategy))
        assertEquals(ConflictStrategyKind.DEFER_TO_USER, ConflictStrategies.kindOf(tasksStrategy))

        val local = Task("1", "local", localVersion = 1, updatedAtMillis = 100)
        val remote = Task("1", "remote", localVersion = 2, updatedAtMillis = 200)
        val deferred = tasksStrategy.resolve(
            ConflictContext(
                entityType = "tasks",
                local = local,
                remote = RemoteMetadata(serverVersion = 2, updatedAtMillis = 200),
                remotePayload = remote,
            ),
        )
        assertIs<ConflictOutcome.Deferred<Task>>(deferred)
        }
    }

    @Test
    fun policy_defaultFromKind() {
        val policy = conflictPolicy {
            default(ConflictStrategyKind.ACCEPT_LOCAL)
        }
        assertEquals(
            ConflictStrategyKind.ACCEPT_LOCAL,
            ConflictStrategies.kindOf(policy.strategyFor("any_entity")),
        )
    }

    @Test
    fun kindOf_returnsNullForUnknownStrategy() {
        val unknown = object : ConflictStrategy {
            override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
                error("unused")
            }
        }
        assertNull(ConflictStrategies.kindOf(unknown))
    }
}