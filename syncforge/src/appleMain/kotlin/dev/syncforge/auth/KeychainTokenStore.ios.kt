@file:OptIn(ExperimentalForeignApi::class)

package dev.syncforge.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus
import platform.CoreFoundation.CFTypeRefVar

/**
 * Keychain-backed token storage (iOS 1.1+ default). Migrates legacy UserDefaults values on first read.
 */
internal class KeychainTokenStore : TokenStore {

    init {
        migrateLegacyUserDefaultsIfNeeded()
    }

    override fun accessToken(): String? = KeychainStorage.load(KEY_ACCESS)

    override fun refreshToken(): String? = KeychainStorage.load(KEY_REFRESH)

    override fun expiresAtMillis(): Long? =
        KeychainStorage.load(KEY_EXPIRES)?.toLongOrNull()

    override fun save(accessToken: String, refreshToken: String?, expiresAtMillis: Long?) {
        KeychainStorage.save(KEY_ACCESS, accessToken)
        if (refreshToken != null) {
            KeychainStorage.save(KEY_REFRESH, refreshToken)
        } else {
            KeychainStorage.delete(KEY_REFRESH)
        }
        if (expiresAtMillis != null) {
            KeychainStorage.save(KEY_EXPIRES, expiresAtMillis.toString())
        } else {
            KeychainStorage.delete(KEY_EXPIRES)
        }
    }

    override fun clear() {
        KeychainStorage.delete(KEY_ACCESS)
        KeychainStorage.delete(KEY_REFRESH)
        KeychainStorage.delete(KEY_EXPIRES)
    }

    private fun migrateLegacyUserDefaultsIfNeeded() {
        if (!accessToken().isNullOrBlank()) return
        val defaults = NSUserDefaults.standardUserDefaults
        val legacyAccess = defaults.stringForKey(LEGACY_KEY_ACCESS) ?: return

        val legacyExpires = defaults.doubleForKey(LEGACY_KEY_EXPIRES)
        save(
            accessToken = legacyAccess,
            refreshToken = defaults.stringForKey(LEGACY_KEY_REFRESH),
            expiresAtMillis = legacyExpires.takeIf { it > 0.0 }?.toLong(),
        )
        defaults.removeObjectForKey(LEGACY_KEY_ACCESS)
        defaults.removeObjectForKey(LEGACY_KEY_REFRESH)
        defaults.removeObjectForKey(LEGACY_KEY_EXPIRES)
        defaults.synchronize()
    }

    companion object {
        const val KEY_ACCESS: String = "access_token"
        const val KEY_REFRESH: String = "refresh_token"
        const val KEY_EXPIRES: String = "expires_at_millis"
        const val SERVICE: String = "dev.syncforge.auth"

        private const val LEGACY_KEY_ACCESS = "syncforge.auth.access"
        private const val LEGACY_KEY_REFRESH = "syncforge.auth.refresh"
        private const val LEGACY_KEY_EXPIRES = "syncforge.auth.expires"
    }
}

private object KeychainStorage {

    fun save(account: String, value: String) {
        delete(account)
        val query = keychainQuery(account) + mapOf<Any?, Any?>(
            kSecValueData to value.encodeToByteArray().toNSData(),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
        )
        SecItemAdd(query as CFDictionaryRef, null)
    }

    fun load(account: String): String? = memScoped {
        val query = keychainQuery(account) + mapOf<Any?, Any?>(
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status: OSStatus = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = result.pointed.value as? NSData ?: return@memScoped null
        data.toUtf8String()
    }

    fun delete(account: String) {
        SecItemDelete(keychainQuery(account) as CFDictionaryRef)
    }

    private fun keychainQuery(account: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to KeychainTokenStore.SERVICE,
        kSecAttrAccount to account,
    )
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
}

private fun NSData.toUtf8String(): String? =
    NSString.create(data = this, encoding = NSUTF8StringEncoding) as String?