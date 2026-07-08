package dev.syncforge.trace

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * OpenTelemetry-compatible span names for SyncForge operations.
 *
 * Wire format: `syncforge.<operation>` (stable across REST, GraphQL, and BaaS transports).
 */
@ExperimentalSyncForgeApi
enum class SyncSpanName(val otelName: String) {
    /** Full push + pull cycle ([SyncManager.sync]). */
    SYNC("syncforge.sync"),

    /** Outbox flush ([SyncManager.push] or push phase inside sync). */
    PUSH("syncforge.push"),

    /** Remote delta fetch + apply ([SyncManager.pull] or pull phase inside sync). */
    PULL("syncforge.pull"),

    /** Conflict detected on pull or resolved by user/auto policy. */
    CONFLICT("syncforge.conflict"),

    /** Scheduled or executed push retry after backoff. */
    RETRY("syncforge.retry"),
}