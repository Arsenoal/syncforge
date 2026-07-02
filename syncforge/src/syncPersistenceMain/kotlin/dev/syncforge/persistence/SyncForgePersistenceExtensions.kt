package dev.syncforge.persistence

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictStore
import dev.syncforge.outbox.OutboxRepository

@ExperimentalSyncForgeApi
fun SyncForgePersistence.outboxRepository(maxRetries: Int = 5): OutboxRepository =
    SqlDelightOutboxRepository(database, maxRetries)

@ExperimentalSyncForgeApi
fun SyncForgePersistence.conflictStore(): ConflictStore =
    SqlDelightConflictStore(database)