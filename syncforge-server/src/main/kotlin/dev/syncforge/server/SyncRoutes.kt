package dev.syncforge.server

import dev.syncforge.network.api.PushRequest
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.syncRoutes(store: SyncStore, includeHealth: Boolean = true) {
    post("/sync/push") {
        val request = call.receive<PushRequest>()
        call.respond(SyncHandlers.push(store, request))
    }

    get("/sync/pull") {
        val params = parsePullQueryParams(call.request.queryParameters)
        call.respond(SyncHandlers.pull(store, params))
    }

    if (includeHealth) {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
    }
}

data class PullQueryParams(
    val since: Long = 0L,
    val types: Set<String> = emptySet(),
    val limit: Int = Int.MAX_VALUE,
    val cursor: String? = null,
)

fun parsePullQueryParams(query: io.ktor.http.Parameters): PullQueryParams =
    parsePullQueryParams(
        since = query["since"],
        types = query["types"],
        limit = query["limit"],
        cursor = query["cursor"],
    )

fun parsePullQueryParams(
    since: String?,
    types: String?,
    limit: String?,
    cursor: String?,
): PullQueryParams {
    val sinceMillis = since?.toLongOrNull() ?: 0L
    val entityTypes = types
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()
    val pageLimit = limit?.toIntOrNull() ?: Int.MAX_VALUE
    return PullQueryParams(since = sinceMillis, types = entityTypes, limit = pageLimit, cursor = cursor)
}