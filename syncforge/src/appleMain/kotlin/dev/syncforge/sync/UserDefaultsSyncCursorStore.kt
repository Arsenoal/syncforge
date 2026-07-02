package dev.syncforge.sync

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/**
 * Persists the pull sync cursor in [NSUserDefaults] — mirrors Android [SharedPreferencesSyncCursorStore].
 */
class UserDefaultsSyncCursorStore(
    private val defaults: NSUserDefaults,
    private val key: String = DEFAULT_KEY,
) : SyncCursorStore {

    override fun get(): Long {
        val stored = defaults.objectForKey(key) as? NSNumber ?: return 0L
        return stored.longLongValue
    }

    override fun set(timestampMillis: Long) {
        defaults.setObject(NSNumber(longLong = timestampMillis), forKey = key)
    }

    companion object {
        const val DEFAULT_KEY: String = "dev.syncforge.last_sync_cursor_millis"
    }
}