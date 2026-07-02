package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity


/**
 * Default conflict strategy: highest [SyncedEntity.updatedAtMillis] wins.
 *
 * Suitable for many CRUD apps; swap for a custom [ConflictResolver] when you need
 * field-level merge or user confirmation.
 */
@Deprecated(
    message = "Use ConflictStrategies.lastWriteWins() in a ConflictPolicy instead. Will be removed in 1.0.",
    replaceWith = ReplaceWith(
        expression = "ConflictStrategies.lastWriteWins()",
        imports = ["dev.syncforge.conflict.ConflictStrategies"],
    ),
)
@ExperimentalSyncForgeApi
@Suppress("DEPRECATION")
class LastWriteWinsResolver : ConflictResolver {

    override fun <T : SyncedEntity> resolve(
        local: T,
        remote: RemoteMetadata,
        remotePayload: T?,
    ): ConflictResolution<T> {
        if (remote.isDeleted) {
            return ConflictResolution.DeleteLocal
        }

        val remoteEntity = remotePayload
            ?: return ConflictResolution.KeepLocal(local)

        return when {
            remote.updatedAtMillis > local.updatedAtMillis ->
                ConflictResolution.AcceptRemote(remoteEntity)

            local.updatedAtMillis > remote.updatedAtMillis ->
                ConflictResolution.KeepLocal(local)

            else -> ConflictResolution.AcceptRemote(remoteEntity)
        }
    }
}