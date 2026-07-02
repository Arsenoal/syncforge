package dev.syncforge.sync

/**
 * Persists the pull sync cursor (server timestamp from the last successful pull).
 */
interface SyncCursorStore {
    fun get(): Long
    fun set(timestampMillis: Long)
}

object InMemorySyncCursorStore : SyncCursorStore {
    private var cursor: Long = 0L

    override fun get(): Long = cursor

    override fun set(timestampMillis: Long) {
        cursor = timestampMillis
    }
}