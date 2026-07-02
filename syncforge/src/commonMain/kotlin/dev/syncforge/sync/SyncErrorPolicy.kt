package dev.syncforge.sync

import dev.syncforge.model.SyncError

internal object SyncErrorPolicy {
    fun isRetryable(code: SyncError.Code): Boolean =
        when (code) {
            SyncError.Code.NETWORK,
            SyncError.Code.SERVER,
            -> true
            SyncError.Code.AUTH,
            SyncError.Code.CONFLICT,
            SyncError.Code.VALIDATION,
            SyncError.Code.UNKNOWN,
            -> false
        }
}