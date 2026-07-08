package dev.syncforge.server

import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse

/**
 * Framework-agnostic sync route handlers shared by Ktor ([syncRoutes]) and Spring controllers.
 */
object SyncHandlers {
    fun push(
        store: SyncStore,
        request: PushRequest,
        nowMillis: Long = System.currentTimeMillis(),
    ): PushResponse = store.push(request.entries, nowMillis)

    fun pull(
        store: SyncStore,
        params: PullQueryParams,
        nowMillis: Long = System.currentTimeMillis(),
    ): PullResponse = store.pull(
        sinceTimestampMillis = params.since,
        entityTypes = params.types,
        nowMillis = nowMillis,
        limit = params.limit,
        pageCursor = params.cursor,
    )
}