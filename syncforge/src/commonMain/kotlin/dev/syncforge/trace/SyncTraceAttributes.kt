package dev.syncforge.trace

import dev.syncforge.api.ExperimentalSyncForgeApi

/** Recommended attribute keys (OpenTelemetry string/long attributes). */
@ExperimentalSyncForgeApi
object SyncTraceAttributes {
    const val OPERATION = "syncforge.operation"
    const val BATCH_SIZE = "syncforge.batch.size"
    const val ACKNOWLEDGED_COUNT = "syncforge.acknowledged.count"
    const val REJECTED_COUNT = "syncforge.rejected.count"
    const val PULLED_COUNT = "syncforge.pulled.count"
    const val CONFLICTS_RESOLVED = "syncforge.conflicts.resolved"
    const val DELETED_COUNT = "syncforge.deleted.count"
    const val SINCE_MILLIS = "syncforge.since.millis"
    const val ENTITY_TYPE = "syncforge.entity.type"
    const val ENTITY_ID = "syncforge.entity.id"
    const val CONFLICT_OUTCOME = "syncforge.conflict.outcome"
    const val ERROR_CODE = "syncforge.error.code"
    const val RETRY_DELAY_MS = "syncforge.retry.delay_ms"
}