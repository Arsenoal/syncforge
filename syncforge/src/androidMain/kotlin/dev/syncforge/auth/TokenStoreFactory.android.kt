package dev.syncforge.auth

import android.content.Context

private object AndroidTokenStoreContext {
    var appContext: Context? = null
}

/** Called from [dev.syncforge.SyncForge.android] before auth service creation. */
internal fun initTokenStoreContext(context: Context) {
    AndroidTokenStoreContext.appContext = context.applicationContext
}

actual fun createTokenStore(): TokenStore {
    val context = AndroidTokenStoreContext.appContext
        ?: error("TokenStore context not initialized — use SyncForge.android(context)")
    return EncryptedTokenStore(context)
}