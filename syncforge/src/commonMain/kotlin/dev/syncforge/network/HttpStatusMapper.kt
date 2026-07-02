package dev.syncforge.network

import dev.syncforge.model.SyncError
import io.ktor.http.HttpStatusCode

internal object HttpStatusMapper {
    fun toSyncError(status: HttpStatusCode, body: String): SyncError {
        val code = when (status.value) {
            401, 403 -> SyncError.Code.AUTH
            409 -> SyncError.Code.CONFLICT
            in 400..499 -> SyncError.Code.VALIDATION
            in 500..599 -> SyncError.Code.SERVER
            else -> SyncError.Code.UNKNOWN
        }
        return SyncError(code = code, message = body.ifBlank { "HTTP ${status.value}" })
    }
}