package dev.syncforge.auth

import dev.syncforge.api.ExperimentalSyncForgeApi

@ExperimentalSyncForgeApi
class BuiltInAuthDsl {
    private var registerPath: String = "/auth/register"
    private var loginPath: String = "/auth/login"
    private var refreshPath: String = "/auth/refresh"
    private var logoutPath: String? = "/auth/logout"
    private var tokenFields: AuthTokenFieldMapping = AuthTokenFieldMapping()
    private var refreshRequestField: String = "refresh_token"
    private var requireAuthForSync: Boolean = true
    private var syncAfterLogin: Boolean = true
    private var syncAfterRegister: Boolean = true

    fun registerPath(path: String) {
        registerPath = path
    }

    fun loginPath(path: String) {
        loginPath = path
    }

    fun refreshPath(path: String) {
        refreshPath = path
    }

    fun logoutPath(path: String?) {
        logoutPath = path
    }

    fun tokenFields(
        accessToken: String = "access_token",
        refreshToken: String = "refresh_token",
        expiresInSeconds: String = "expires_in",
    ) {
        tokenFields = AuthTokenFieldMapping(accessToken, refreshToken, expiresInSeconds)
    }

    fun refreshRequestField(fieldName: String) {
        refreshRequestField = fieldName
    }

    fun requireAuthForSync(enabled: Boolean = true) {
        requireAuthForSync = enabled
    }

    fun syncAfterLogin(enabled: Boolean = true) {
        syncAfterLogin = enabled
    }

    fun syncAfterRegister(enabled: Boolean = true) {
        syncAfterRegister = enabled
    }

    internal fun build(): BuiltInAuthConfig = BuiltInAuthConfig(
        registerPath = registerPath,
        loginPath = loginPath,
        refreshPath = refreshPath,
        logoutPath = logoutPath,
        tokenFields = tokenFields,
        refreshRequestField = refreshRequestField,
        requireAuthForSync = requireAuthForSync,
        syncAfterLogin = syncAfterLogin,
        syncAfterRegister = syncAfterRegister,
    )
}