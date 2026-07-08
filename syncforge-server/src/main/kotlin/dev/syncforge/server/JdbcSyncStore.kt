package dev.syncforge.server

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PushRejectionDto
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.RemoteDeltaDto
import java.sql.Connection
import java.util.Base64
import javax.sql.DataSource

/**
 * JDBC-backed [SyncStore] for production Spring Boot or custom JVM backends.
 *
 * Schema is created by Flyway in [:backend-starter-spring] or via [ensureSchema].
 */
class JdbcSyncStore(
    private val dataSource: DataSource,
) : SyncStore {

    override fun push(entries: List<OutboxEntryDto>, nowMillis: Long): PushResponse {
        val acknowledged = mutableListOf<Long>()
        val rejected = mutableListOf<PushRejectionDto>()

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                entries.forEach { entry ->
                    when (val outcome = pushEntry(connection, entry, nowMillis)) {
                        is PushEntryOutcome.Acknowledged -> acknowledged += outcome.outboxId
                        is PushEntryOutcome.Rejected -> rejected += outcome.rejection
                    }
                }
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            }
        }

        return PushResponse(
            acknowledgedIds = acknowledged,
            rejected = rejected,
        )
    }

    override fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        nowMillis: Long,
        limit: Int,
        pageCursor: String?,
    ): PullResponse {
        dataSource.connection.use { connection ->
            val allDeltas = queryDeltas(connection, sinceTimestampMillis, entityTypes)
            val startIndex = decodeCursor(pageCursor).coerceIn(0, allDeltas.size)
            val endIndex = (startIndex + limit).coerceAtMost(allDeltas.size)
            val page = allDeltas.subList(startIndex, endIndex)
            val hasMore = endIndex < allDeltas.size

            return PullResponse(
                deltas = page,
                serverTimestampMillis = nowMillis,
                hasMore = hasMore,
                nextPageCursor = if (hasMore) encodeCursor(endIndex) else null,
            )
        }
    }

    private sealed interface PushEntryOutcome {
        data class Acknowledged(val outboxId: Long) : PushEntryOutcome
        data class Rejected(val rejection: PushRejectionDto) : PushEntryOutcome
    }

    private fun pushEntry(
        connection: Connection,
        entry: OutboxEntryDto,
        nowMillis: Long,
    ): PushEntryOutcome {
        if (entry.entityType.isBlank() || entry.entityId.isBlank()) {
            return PushEntryOutcome.Rejected(
                PushRejectionDto(
                    outboxId = entry.id,
                    code = "VALIDATION",
                    message = "entityType and entityId are required",
                ),
            )
        }

        val existing = loadRecord(connection, entry.entityType, entry.entityId)
        if (
            existing?.isDeleted == true &&
            (entry.changeType == ChangeType.CREATE || entry.changeType == ChangeType.UPDATE)
        ) {
            return PushEntryOutcome.Rejected(
                PushRejectionDto(
                    outboxId = entry.id,
                    code = "CONFLICT",
                    message = "Entity was deleted on the server",
                ),
            )
        }

        if (
            entry.changeType == ChangeType.UPDATE &&
            existing != null &&
            existing.serverVersion != entry.localVersion - 1
        ) {
            return PushEntryOutcome.Rejected(
                PushRejectionDto(
                    outboxId = entry.id,
                    code = "CONFLICT",
                    message = "Server version ${existing.serverVersion} does not match expected base ${entry.localVersion - 1}",
                ),
            )
        }

        val nextVersion = allocateServerVersion(connection)
        val record = when (entry.changeType) {
            ChangeType.DELETE -> EntityRecord(
                entityType = entry.entityType,
                entityId = entry.entityId,
                payloadJson = null,
                serverVersion = nextVersion,
                updatedAtMillis = nowMillis,
                isDeleted = true,
            )
            ChangeType.CREATE, ChangeType.UPDATE -> EntityRecord(
                entityType = entry.entityType,
                entityId = entry.entityId,
                payloadJson = entry.payloadJson,
                serverVersion = nextVersion,
                updatedAtMillis = nowMillis,
                isDeleted = false,
            )
        }
        upsertRecord(connection, record, hadExistingRow = existing != null)
        return PushEntryOutcome.Acknowledged(entry.id)
    }

    private data class EntityRecord(
        val entityType: String,
        val entityId: String,
        val payloadJson: String?,
        val serverVersion: Long,
        val updatedAtMillis: Long,
        val isDeleted: Boolean,
    )

    private fun loadRecord(
        connection: Connection,
        entityType: String,
        entityId: String,
    ): EntityRecord? {
        connection.prepareStatement(
            """
            SELECT payload_json, server_version, updated_at_millis, is_deleted
            FROM sync_entity
            WHERE entity_type = ? AND entity_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, entityType)
            statement.setString(2, entityId)
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                return EntityRecord(
                    entityType = entityType,
                    entityId = entityId,
                    payloadJson = result.getString("payload_json"),
                    serverVersion = result.getLong("server_version"),
                    updatedAtMillis = result.getLong("updated_at_millis"),
                    isDeleted = result.getBoolean("is_deleted"),
                )
            }
        }
    }

    private fun upsertRecord(connection: Connection, record: EntityRecord, hadExistingRow: Boolean) {
        if (hadExistingRow) {
            connection.prepareStatement(
                """
                UPDATE sync_entity
                SET payload_json = ?, server_version = ?, updated_at_millis = ?, is_deleted = ?
                WHERE entity_type = ? AND entity_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, record.payloadJson)
                statement.setLong(2, record.serverVersion)
                statement.setLong(3, record.updatedAtMillis)
                statement.setBoolean(4, record.isDeleted)
                statement.setString(5, record.entityType)
                statement.setString(6, record.entityId)
                statement.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                """
                INSERT INTO sync_entity (
                    entity_type, entity_id, payload_json, server_version, updated_at_millis, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, record.entityType)
                statement.setString(2, record.entityId)
                statement.setString(3, record.payloadJson)
                statement.setLong(4, record.serverVersion)
                statement.setLong(5, record.updatedAtMillis)
                statement.setBoolean(6, record.isDeleted)
                statement.executeUpdate()
            }
        }
    }

    private fun allocateServerVersion(connection: Connection): Long {
        connection.prepareStatement(
            """
            UPDATE sync_version_counter
            SET next_version = next_version + 1
            WHERE id = 1
            """.trimIndent(),
        ).use { statement ->
            check(statement.executeUpdate() == 1) { "sync_version_counter row missing" }
        }
        connection.prepareStatement(
            """
            SELECT next_version
            FROM sync_version_counter
            WHERE id = 1
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                check(result.next()) { "sync_version_counter row missing" }
                return result.getLong("next_version")
            }
        }
    }

    private fun queryDeltas(
        connection: Connection,
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
    ): List<RemoteDeltaDto> {
        val sql = buildString {
            append(
                """
                SELECT entity_type, entity_id, payload_json, server_version, updated_at_millis, is_deleted
                FROM sync_entity
                WHERE updated_at_millis > ?
                """.trimIndent(),
            )
            if (entityTypes.isNotEmpty()) {
                append(" AND entity_type IN (")
                append(entityTypes.joinToString(", ") { "?" })
                append(")")
            }
            append(" ORDER BY updated_at_millis ASC")
        }

        connection.prepareStatement(sql).use { statement ->
            var index = 1
            statement.setLong(index++, sinceTimestampMillis)
            entityTypes.forEach { type ->
                statement.setString(index++, type)
            }
            statement.executeQuery().use { result ->
                val deltas = mutableListOf<RemoteDeltaDto>()
                while (result.next()) {
                    deltas += RemoteDeltaDto(
                        entityType = result.getString("entity_type"),
                        entityId = result.getString("entity_id"),
                        payloadJson = result.getString("payload_json"),
                        serverVersion = result.getLong("server_version"),
                        updatedAtMillis = result.getLong("updated_at_millis"),
                        isDeleted = result.getBoolean("is_deleted"),
                    )
                }
                return deltas
            }
        }
    }

    private fun encodeCursor(index: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(index.toString().toByteArray())

    private fun decodeCursor(cursor: String?): Int {
        if (cursor.isNullOrBlank()) return 0
        return String(Base64.getUrlDecoder().decode(cursor)).toIntOrNull() ?: 0
    }

    companion object {
        fun ensureSchema(dataSource: DataSource) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        CREATE TABLE IF NOT EXISTS sync_entity (
                            entity_type VARCHAR(255) NOT NULL,
                            entity_id VARCHAR(255) NOT NULL,
                            payload_json CLOB,
                            server_version BIGINT NOT NULL,
                            updated_at_millis BIGINT NOT NULL,
                            is_deleted BOOLEAN NOT NULL,
                            PRIMARY KEY (entity_type, entity_id)
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        CREATE TABLE IF NOT EXISTS sync_version_counter (
                            id INT PRIMARY KEY,
                            next_version BIGINT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        INSERT INTO sync_version_counter (id, next_version)
                        SELECT 1, 1
                        WHERE NOT EXISTS (SELECT 1 FROM sync_version_counter WHERE id = 1)
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}