package dev.syncforge.transport

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.SyncTransport
import dev.syncforge.network.SyncTransportException

/**
 * General [SyncTransport] adapter — maps engine push/pull to any [SyncDeltaStore].
 *
 * Use for Firebase, Supabase, custom JDBC, or other hosted stores without duplicating
 * push/pull mapping per vendor:
 *
 * ```
 * SyncForge.android(this) {
 *     transport(DeltaStoreSyncTransport(myFirestoreSyncDeltaStore))
 * }
 * ```
 *
 * Authentication and wire format are owned by the [SyncDeltaStore] implementation.
 */
@ExperimentalSyncForgeApi
class DeltaStoreSyncTransport(
    private val store: SyncDeltaStore,
) : SyncTransport {

    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        runTransport { store.appendEntries(entries) }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = runTransport {
        store.queryDeltas(
            sinceTimestampMillis = sinceTimestampMillis,
            entityTypes = entityTypes,
            pageSize = pageSize,
            pageCursor = pageCursor,
        )
    }

    private suspend inline fun <T> runTransport(block: suspend () -> T): T =
        try {
            block()
        } catch (e: SyncTransportException) {
            throw e
        } catch (e: Exception) {
            throw SyncTransportException(
                SyncError(
                    code = SyncError.Code.NETWORK,
                    message = e.message ?: "SyncDeltaStore operation failed",
                    cause = e,
                ),
            )
        }
}