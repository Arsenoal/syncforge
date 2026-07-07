@file:OptIn(dev.syncforge.api.ExperimentalSyncForgeApi::class)

package dev.syncforge.conflict

import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncState
import dev.syncforge.network.RemoteDelta
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrdtMergeStrategyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun orSetField_unionsConcurrentTagAdds() {
        runBlocking {
            val strategy = crdtPolicy().strategyFor("documents")
            val local = TaggedDocument("1", tags = listOf("alpha"), updatedAtMillis = 100)
            val remote = TaggedDocument("1", tags = listOf("beta"), updatedAtMillis = 200)

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "documents",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200),
                    remotePayload = remote,
                ),
            )

            assertIs<ConflictOutcome.Resolved<TaggedDocument>>(outcome)
            val merged = (outcome as ConflictOutcome.Resolved).resolution
            assertIs<ConflictResolution.Merged<TaggedDocument>>(merged)
            assertEquals(setOf("alpha", "beta"), merged.entity.tags.toSet())
        }
    }

    @Test
    fun gCounterField_takesMaxForPrimitiveCounters() {
        runBlocking {
            val policy = conflictPolicy {
                entity("metrics") {
                    crdt<TaggedDocument> {
                        field("views") { gCounter() }
                    }
                }
            }
            val strategy = policy.strategyFor("metrics")
            val local = TaggedDocument("1", views = 4, updatedAtMillis = 300)
            val remote = TaggedDocument("1", views = 7, updatedAtMillis = 100)

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "metrics",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 2, updatedAtMillis = 100),
                    remotePayload = remote,
                ),
            )

            assertIs<ConflictOutcome.Resolved<TaggedDocument>>(outcome)
            val merged = (outcome as ConflictOutcome.Resolved).resolution
            assertIs<ConflictResolution.Merged<TaggedDocument>>(merged)
            assertEquals(7, merged.entity.views)
        }
    }

    @Test
    fun pullApplier_mergesConcurrentTagsOnConflict() {
        runBlocking {
            val handler = TestDocumentHandler(
                initial = TaggedDocument(
                    id = "1",
                    tags = listOf("alpha"),
                    updatedAtMillis = 100,
                    syncState = SyncState.PENDING,
                ),
            )
            val applier = ConflictPullApplier(
                policy = crdtPolicy(),
                conflictStore = NoOpConflictStore,
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = RemoteDelta(
                    entityType = "documents",
                    entityId = "1",
                    payloadJson = json.encodeToString(TaggedDocument("1", tags = listOf("beta"), updatedAtMillis = 200)),
                    serverVersion = 3L,
                    updatedAtMillis = 200L,
                ),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.current()
            assertEquals(setOf("alpha", "beta"), persisted?.tags?.toSet())
            assertEquals(SyncState.SYNCED, persisted?.syncState)
        }
    }

    @Test
    fun kindOf_recognizesCrdtStrategy() {
        val policy = crdtPolicy()
        assertEquals(ConflictStrategyKind.CRDT, ConflictStrategies.kindOf(policy.strategyFor("documents")))
    }

    private fun crdtPolicy(): ConflictPolicy =
        conflictPolicy {
            entity("documents") {
                crdt<TaggedDocument> {
                    field("tags") { orSet() }
                }
            }
        }

    @Serializable
    data class TaggedDocument(
        override val id: String,
        val tags: List<String> = emptyList(),
        val views: Int = 0,
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private class TestDocumentHandler(
        initial: TaggedDocument,
    ) : TypedEntitySyncHandler<TaggedDocument>() {

        override val entityType: String = "documents"
        private var entity: TaggedDocument = initial
        private val json = Json { ignoreUnknownKeys = true }

        override fun toJson(entity: TaggedDocument): String = json.encodeToString(entity)

        override fun fromJson(json: String): TaggedDocument = this.json.decodeFromString(json)

        override suspend fun findById(id: String): TaggedDocument? = entity.takeIf { it.id == id }

        override suspend fun insert(entity: TaggedDocument) {
            this.entity = entity
        }

        override suspend fun update(entity: TaggedDocument) {
            this.entity = entity
        }

        override suspend fun deleteById(id: String) {
            if (entity.id == id) entity = entity.copy(syncState = SyncState.SYNCED)
        }

        override fun withSyncState(entity: TaggedDocument, state: SyncState): TaggedDocument =
            entity.copy(syncState = state)

        fun current(): TaggedDocument = entity
    }
}