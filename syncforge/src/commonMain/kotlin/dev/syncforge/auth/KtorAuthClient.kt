package dev.syncforge.auth

import dev.syncforge.network.SyncTransportException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class KtorAuthClient(
    private val baseUrl: String,
    private val config: BuiltInAuthConfig,
    private val httpClient: HttpClient,
    private val parser: TokenResponseParser = TokenResponseParser(),
) {
    private val normalizedBase = baseUrl.trimEnd('/')

    suspend fun register(fields: Map<String, String>): ParsedAuthResponse =
        postAuth(config.registerPath, fields)

    suspend fun login(fields: Map<String, String>): ParsedAuthResponse =
        postAuth(config.loginPath, fields)

    suspend fun refresh(refreshToken: String): ParsedAuthResponse =
        postAuth(config.refreshPath, mapOf(config.refreshRequestField to refreshToken))

    suspend fun logout() {
        val path = config.logoutPath ?: return
        runCatching {
            httpClient.post("$normalizedBase$path") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { })
            }
        }
    }

    private suspend fun postAuth(path: String, fields: Map<String, String>): ParsedAuthResponse {
        val body = buildJsonObject {
            fields.forEach { (key, value) -> put(key, value) }
        }
        return try {
            val responseText = httpClient.post("$normalizedBase$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body<String>()
            val tokens = parser.parse(responseText, config.tokenFields)
            ParsedAuthResponse(
                tokens = tokens,
                session = Session(
                    expiresAtMillis = tokens.expiresAtMillis,
                    hasRefreshToken = !tokens.refreshToken.isNullOrBlank(),
                ),
            )
        } catch (e: SyncTransportException) {
            throw AuthRequestException(e.error.toAuthError())
        } catch (e: AuthRequestException) {
            throw e
        } catch (e: Exception) {
            throw AuthRequestException(
                AuthError(
                    code = AuthError.Code.NETWORK,
                    message = e.message ?: "Auth request failed",
                ),
            )
        }
    }

    companion object {
        fun create(
            baseUrl: String,
            config: BuiltInAuthConfig,
            engine: HttpClientEngine? = null,
        ): KtorAuthClient {
            val client = if (engine != null) {
                createAuthHttpClient(engine, AuthJson)
            } else {
                createAuthHttpClient(AuthJson)
            }
            return KtorAuthClient(baseUrl, config, client)
        }

        private val AuthJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}

internal data class ParsedAuthResponse(
    val tokens: ParsedTokens,
    val session: Session,
)

internal class AuthRequestException(val error: AuthError) : Exception(error.message)

internal fun dev.syncforge.model.SyncError.toAuthError(): AuthError =
    AuthError(
        code = when (code) {
            dev.syncforge.model.SyncError.Code.NETWORK -> AuthError.Code.NETWORK
            dev.syncforge.model.SyncError.Code.AUTH -> AuthError.Code.AUTH
            dev.syncforge.model.SyncError.Code.VALIDATION -> AuthError.Code.VALIDATION
            dev.syncforge.model.SyncError.Code.SERVER -> AuthError.Code.SERVER
            dev.syncforge.model.SyncError.Code.CONFLICT,
            dev.syncforge.model.SyncError.Code.UNKNOWN,
            -> AuthError.Code.UNKNOWN
        },
        message = message,
        httpStatus = httpStatus,
    )