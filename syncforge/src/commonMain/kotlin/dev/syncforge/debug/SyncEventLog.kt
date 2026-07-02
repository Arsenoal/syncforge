package dev.syncforge.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SyncEventLog(
    private val maxEvents: Int = 100,
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private val _events = MutableStateFlow<List<SyncEvent>>(emptyList())
    val events: StateFlow<List<SyncEvent>> = _events.asStateFlow()

    private var nextId = 1L

    suspend fun record(
        type: SyncEventType,
        success: Boolean,
        summary: String,
        errorCode: dev.syncforge.model.SyncError.Code? = null,
    ) = mutex.withLock {
        val event = SyncEvent(
            id = nextId++,
            timestampMillis = clock(),
            type = type,
            success = success,
            summary = summary,
            errorCode = errorCode,
        )
        _events.value = (listOf(event) + _events.value).take(maxEvents)
    }

    suspend fun clear() = mutex.withLock {
        _events.value = emptyList()
    }
}