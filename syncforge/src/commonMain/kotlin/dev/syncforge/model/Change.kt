package dev.syncforge.model

import dev.syncforge.entity.SyncedEntity

/**
 * A single local mutation to be recorded in the outbox and pushed upstream.
 *
 * @param T entity type implementing [SyncedEntity].
 * @param payload `null` for [ChangeType.DELETE]; serialized to JSON in the outbox.
 */
data class Change<T : SyncedEntity>(
    val entityType: String,
    val entityId: String,
    val type: ChangeType,
    val payload: T? = null,
    val localVersion: Long,
    val updatedAtMillis: Long,
) {
    init {
        require(entityType.isNotBlank()) { "entityType must not be blank" }
        require(entityId.isNotBlank()) { "entityId must not be blank" }
        require(localVersion >= 0) { "localVersion must be non-negative" }
        if (type == ChangeType.DELETE) {
            require(payload == null) { "DELETE changes must not carry a payload" }
        } else {
            require(payload != null) { "${type.name} changes require a payload" }
        }
    }

    companion object {
        fun <T : SyncedEntity> create(entityType: String, entity: T): Change<T> =
            Change(
                entityType = entityType,
                entityId = entity.id,
                type = ChangeType.CREATE,
                payload = entity,
                localVersion = entity.localVersion,
                updatedAtMillis = entity.updatedAtMillis,
            )

        fun <T : SyncedEntity> update(entityType: String, entity: T): Change<T> =
            Change(
                entityType = entityType,
                entityId = entity.id,
                type = ChangeType.UPDATE,
                payload = entity,
                localVersion = entity.localVersion,
                updatedAtMillis = entity.updatedAtMillis,
            )

        fun <T : SyncedEntity> delete(
            entityType: String,
            entityId: String,
            localVersion: Long,
            updatedAtMillis: Long,
        ): Change<T> =
            Change(
                entityType = entityType,
                entityId = entityId,
                type = ChangeType.DELETE,
                payload = null,
                localVersion = localVersion,
                updatedAtMillis = updatedAtMillis,
            )
    }
}