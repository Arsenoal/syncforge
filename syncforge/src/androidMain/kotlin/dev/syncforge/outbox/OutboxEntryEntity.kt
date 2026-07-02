package dev.syncforge.outbox

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "syncforge_outbox")
internal data class OutboxEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val changeType: String,
    val payloadJson: String?,
    val rollbackSnapshotJson: String?,
    val localVersion: Long,
    val createdAtMillis: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val nextRetryAtMillis: Long? = null,
)