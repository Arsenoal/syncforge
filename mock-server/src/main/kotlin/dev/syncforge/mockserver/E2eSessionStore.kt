package dev.syncforge.mockserver

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session store for two-emulator E2E coordination (1.4-06).
 * Devices publish shared keys (entity ids, titles) and barrier signals via HTTP.
 */
class E2eSessionStore {

    private val sessions = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    fun resetAll() {
        sessions.clear()
    }

    fun create(sessionId: String) {
        sessions[sessionId] = ConcurrentHashMap()
    }

    fun put(sessionId: String, key: String, value: String) {
        val session = sessions[sessionId]
            ?: error("Unknown E2E session: $sessionId")
        session[key] = value
    }

    fun get(sessionId: String, key: String): String? =
        sessions[sessionId]?.get(key)

    fun snapshot(sessionId: String): Map<String, String> =
        sessions[sessionId]?.toMap().orEmpty()
}