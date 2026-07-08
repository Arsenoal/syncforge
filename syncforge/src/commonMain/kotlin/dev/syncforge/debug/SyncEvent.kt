package dev.syncforge.debug

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.SyncError

@ExperimentalSyncForgeApi
enum class SyncEventType {
    FULL_SYNC,
    PUSH,
    PULL,
    OUTBOX_CLEARED,
    CONFLICT_OPENED,
    CONFLICT_RESOLVED,
    ENQUEUE,
}

@ExperimentalSyncForgeApi
data class SyncEvent(
    val id: Long,
    val timestampMillis: Long,
    val type: SyncEventType,
    val success: Boolean,
    val summary: String,
    val errorCode: SyncError.Code? = null,
    val durationMillis: Long? = null,
)