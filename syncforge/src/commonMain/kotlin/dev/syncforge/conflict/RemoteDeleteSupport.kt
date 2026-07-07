package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

/** Default remote-tombstone handling — delete the local row (accept server delete). */
@PublishedApi
internal object DeleteLocalOnRemoteTombstoneStrategy : ConflictStrategy {

    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
        require(context.remote.isDeleted) {
            "DeleteLocalOnRemoteTombstoneStrategy applies only when remote.isDeleted"
        }
        return ConflictOutcome.Resolved(ConflictResolution.DeleteLocal)
    }
}

internal suspend fun <T : SyncedEntity> ConflictStrategy.resolveRemoteDelete(
    context: ConflictContext<T>,
): ConflictOutcome<T> {
    require(context.remote.isDeleted) { "resolveRemoteDelete requires remote.isDeleted" }
    return resolve(context)
}

internal fun ConflictEntityBuilder.strategyForRemoteDelete(
    block: ConflictEntityBuilder.() -> Unit,
): ConflictStrategy {
    val builder = ConflictEntityBuilder().apply(block)
    return builder.strategy ?: DeleteLocalOnRemoteTombstoneStrategy
}