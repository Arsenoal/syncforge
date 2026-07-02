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
        val response = store.push(request.entries, System.currentTimeMillis())
        call.respond(response)
    }

    get("/sync/pull") {
        val params = parsePullQueryParams(call.request.queryParameters)
        val response = store.pull(
            sinceTimestampMillis = params.since,
            entityTypes = params.types,
            nowMillis = System.currentTimeMillis(),
            limit = params.limit,
            pageCursor = params.cursor,
        )
        call.respond(response)
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

fun parsePullQueryParams(query: io.ktor.http.Parameters): PullQueryParams {
    val since = query["since"]?.toLongOrNull() ?: 0L
    val types = query["types"]
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()
    val limit = query["limit"]?.toIntOrNull() ?: Int.MAX_VALUE
    val cursor = query["cursor"]
    return PullQueryParams(since = since, types = types, limit = limit, cursor = cursor)
}