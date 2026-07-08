package dev.syncforge.network

import dev.syncforge.model.SyncError
import io.ktor.http.HttpStatusCode

internal object HttpStatusMapper {
    fun toSyncError(
        status: HttpStatusCode,
        body: String,
        retryAfterHeader: String? = null,
    ): SyncError {
        val code = when (status.value) {
            401, 403 -> SyncError.Code.AUTH
            409 -> SyncError.Code.CONFLICT
            429 -> SyncError.Code.SERVER
            in 400..499 -> SyncError.Code.VALIDATION
            in 500..599 -> SyncError.Code.SERVER
            else -> SyncError.Code.UNKNOWN
        }
        return SyncError(
            code = code,
            message = body.ifBlank { "HTTP ${status.value}" },
            httpStatus = status.value,
            retryAfterMillis = parseRetryAfterMillis(retryAfterHeader),
        )
    }

    fun parseRetryAfterMillis(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val trimmed = header.trim()
        trimmed.toLongOrNull()?.let { seconds -> return seconds * 1_000 }
        return null
    }
}