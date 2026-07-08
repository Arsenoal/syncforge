package dev.syncforge.debug

import dev.syncforge.api.ExperimentalSyncForgeApi

/** Output format for conflict audit exports (1.5-06). */
@ExperimentalSyncForgeApi
enum class AuditLogFormat {
    JSON,
    CSV,
}