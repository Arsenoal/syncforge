package dev.syncforge.auth

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.network.RefreshingSyncAuthProvider
import dev.syncforge.network.SyncAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Built-in register/login/refresh/logout orchestration.
 *
 * Wired automatically when using `auth { }` in [dev.syncforge.SyncForge.android].
 */
@ExperimentalSyncForgeApi
class SyncForgeAuthService internal constructor(
    val config: BuiltInAuthConfig,
    private val tokenStore: TokenStore,
    private val client: KtorAuthClient,
) {
    private val mutex = Mutex()
    private val _authState = MutableStateFlow<AuthState>(initialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val authProvider: SyncAuthProvider = RefreshingSyncAuthProvider(
        accessTokenProvider = { tokenStore.accessToken() },
        refresh = { refreshAccessToken() },
    )

    val session: Session?
        get() = when (val state = _authState.value) {
            is AuthState.LoggedIn -> Session(
                expiresAtMillis = state.expiresAtMillis,
                hasRefreshToken = !tokenStore.refreshToken().isNullOrBlank(),
            )
            else -> null
        }

    suspend fun register(fields: Map<String, String>): AuthResult =
        mutex.withLock {
            runAuth { client.register(fields) }
        }

    suspend fun login(fields: Map<String, String>): AuthResult =
        mutex.withLock {
            runAuth { client.login(fields) }
        }

    suspend fun logout(): AuthResult = mutex.withLock {
        runCatching { client.logout() }
        tokenStore.clear()
        _authState.value = AuthState.LoggedOut
        AuthResult.Success(Session(expiresAtMillis = null, hasRefreshToken = false))
    }

    suspend fun refreshAccessToken(): String? = mutex.withLock {
        val refresh = tokenStore.refreshToken() ?: return@withLock null
        val previous = _authState.value
        _authState.value = AuthState.Refreshing
        try {
            val response = client.refresh(refresh)
            applyTokens(response)
            tokenStore.accessToken()
        } catch (e: AuthRequestException) {
            tokenStore.clear()
            _authState.value = AuthState.Error(e.error.message)
            null
        } catch (e: Exception) {
            _authState.value = previous
            null
        }
    }

    fun requireAuthenticated(): AuthResult.Failure? {
        if (!config.requireAuthForSync) return null
        return when (_authState.value) {
            is AuthState.LoggedIn -> null
            is AuthState.Refreshing -> AuthResult.Failure(
                AuthError(AuthError.Code.AUTH, "Authentication in progress"),
            )
            is AuthState.Error -> AuthResult.Failure(
                AuthError(AuthError.Code.AUTH, (_authState.value as AuthState.Error).message),
            )
            AuthState.LoggedOut -> AuthResult.Failure(
                AuthError(AuthError.Code.AUTH, "Not authenticated — call login() or register() first"),
            )
        }
    }

    private suspend fun runAuth(block: suspend () -> ParsedAuthResponse): AuthResult {
        return try {
            val response = block()
            applyTokens(response)
            AuthResult.Success(response.session)
        } catch (e: AuthRequestException) {
            _authState.value = AuthState.Error(e.error.message)
            AuthResult.Failure(e.error)
        }
    }

    private fun applyTokens(response: ParsedAuthResponse) {
        tokenStore.save(
            accessToken = response.tokens.accessToken,
            refreshToken = response.tokens.refreshToken,
            expiresAtMillis = response.tokens.expiresAtMillis,
        )
        _authState.value = AuthState.LoggedIn(response.tokens.expiresAtMillis)
    }

    private fun initialAuthState(): AuthState =
        if (!tokenStore.accessToken().isNullOrBlank()) {
            AuthState.LoggedIn(tokenStore.expiresAtMillis())
        } else {
            AuthState.LoggedOut
        }

    companion object {
        fun create(
            baseUrl: String,
            config: BuiltInAuthConfig,
            tokenStore: TokenStore,
            engine: io.ktor.client.engine.HttpClientEngine? = null,
        ): SyncForgeAuthService = SyncForgeAuthService(
            config = config,
            tokenStore = tokenStore,
            client = KtorAuthClient.create(baseUrl, config, engine),
        )
    }
}