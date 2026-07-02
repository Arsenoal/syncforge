package dev.syncforge.auth

/** Session lifecycle exposed on [dev.syncforge.sync.SyncManager.authState]. */
sealed class AuthState {
    data object LoggedOut : AuthState()
    data class LoggedIn(val expiresAtMillis: Long? = null) : AuthState()
    data object Refreshing : AuthState()
    data class Error(val message: String) : AuthState()

    val isLoggedIn: Boolean get() = this is LoggedIn
}