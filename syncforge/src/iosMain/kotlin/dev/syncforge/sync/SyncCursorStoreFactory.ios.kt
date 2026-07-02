package dev.syncforge.sync

import platform.Foundation.NSUserDefaults

/**
 * iOS factory for [SyncCursorStore]. Use [suiteName] for App Group shared defaults.
 */
object SyncCursorStoreFactory {

    fun create(
        suiteName: String? = null,
        key: String = UserDefaultsSyncCursorStore.DEFAULT_KEY,
    ): SyncCursorStore {
        val defaults = resolveDefaults(suiteName)
        return UserDefaultsSyncCursorStore(defaults = defaults, key = key)
    }

    private fun resolveDefaults(suiteName: String?): NSUserDefaults =
        if (suiteName != null) {
            NSUserDefaults(suiteName = suiteName) ?: NSUserDefaults.standardUserDefaults
        } else {
            NSUserDefaults.standardUserDefaults
        }
}