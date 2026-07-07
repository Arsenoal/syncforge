package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

internal class LastWriteWinsStrategy : ConflictStrategy {

    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
        if (context.remote.isDeleted) {
            val resolution = when {
                context.local.updatedAtMillis > context.remote.updatedAtMillis ->
                    ConflictResolution.KeepLocal(context.local)

                else -> ConflictResolution.DeleteLocal
            }
            return ConflictOutcome.Resolved(resolution)
        }

        val remoteEntity = context.remotePayload
            ?: return ConflictOutcome.Resolved(ConflictResolution.KeepLocal(context.local))

        val resolution = when {
            context.remote.updatedAtMillis > context.local.updatedAtMillis ->
                ConflictResolution.AcceptRemote(remoteEntity)

            context.local.updatedAtMillis > context.remote.updatedAtMillis ->
                ConflictResolution.KeepLocal(context.local)

            else -> ConflictResolution.AcceptRemote(remoteEntity)
        }
        return ConflictOutcome.Resolved(resolution)
    }
}