package dev.syncforge.outbox

import dev.syncforge.model.OutboxEntry

internal fun List<OutboxEntry>.readyForPush(nowMillis: Long, maxRetries: Int, limit: Int): List<OutboxEntry> =
    asSequence()
        .filter { it.isReadyForPush(nowMillis, maxRetries) }
        .take(limit)
        .toList()

internal fun List<OutboxEntry>.countAwaitingPush(maxRetries: Int): Int =
    count { !it.isPermanentlyFailed(maxRetries) }

internal fun List<OutboxEntry>.countPermanentlyFailed(maxRetries: Int): Int =
    count { it.isPermanentlyFailed(maxRetries) }

internal fun List<OutboxEntry>.earliestRetryAtMillis(maxRetries: Int): Long? =
    asSequence()
        .filter { !it.isPermanentlyFailed(maxRetries) && it.nextRetryAtMillis != null }
        .mapNotNull { it.nextRetryAtMillis }
        .minOrNull()