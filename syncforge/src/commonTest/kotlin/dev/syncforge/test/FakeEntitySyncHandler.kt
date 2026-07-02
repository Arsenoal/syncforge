package dev.syncforge.test

import dev.syncforge.entity.EntitySyncHandler
import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.RemoteDelta

class FakeEntitySyncHandler(
    override val entityType: String,
) : EntitySyncHandler {

    val snapshots = mutableListOf<String?>()
    val optimisticChanges = mutableListOf<Change<*>>()
    val rollbackEntries = mutableListOf<OutboxEntry>()
    val acknowledgedIds = mutableListOf<String>()
    val pullDeltas = mutableListOf<RemoteDelta>()

    var pullOutcome: PullApplyOutcome = PullApplyOutcome.UPDATED
    var snapshotToReturn: String? = null
    var serializedPayload: String? = """{"id":"stub"}"""

    override suspend fun captureSnapshot(entityId: String): String? {
        snapshots += snapshotToReturn
        return snapshotToReturn
    }

    override suspend fun applyOptimistic(change: Change<*>) {
        optimisticChanges += change
    }

    override suspend fun rollbackEntry(entry: OutboxEntry) {
        rollbackEntries += entry
    }

    override suspend fun onPushAcknowledged(entryEntityId: String) {
        acknowledgedIds += entryEntityId
    }

    override suspend fun applyPullDelta(delta: RemoteDelta): PullApplyOutcome {
        pullDeltas += delta
        return pullOutcome
    }

    override fun serializeChange(change: Change<*>): String? = serializedPayload
}