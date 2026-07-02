package dev.syncforge.sync

import java.io.File
import java.util.Properties

/**
 * File-backed pull cursor for JVM desktop — step toward multiplatform persistence (M5).
 */
internal class FileSyncCursorStore(
    private val file: File,
    private val key: String = DEFAULT_KEY,
) : SyncCursorStore {

    private val properties = Properties()

    init {
        if (file.exists()) {
            file.inputStream().use { properties.load(it) }
        }
    }

    override fun get(): Long =
        properties.getProperty(key)?.toLongOrNull() ?: 0L

    override fun set(timestampMillis: Long) {
        properties.setProperty(key, timestampMillis.toString())
        file.parentFile?.mkdirs()
        file.outputStream().use { properties.store(it, "SyncForge pull cursor") }
    }

    companion object {
        const val DEFAULT_KEY: String = "dev.syncforge.last_sync_cursor_millis"
    }
}