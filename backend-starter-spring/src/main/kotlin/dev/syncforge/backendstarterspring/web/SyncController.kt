package dev.syncforge.backendstarterspring.web

import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse
import dev.syncforge.server.SyncHandlers
import dev.syncforge.server.SyncStore
import dev.syncforge.server.parsePullQueryParams
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sync")
class SyncController(
    private val syncStore: SyncStore,
) {
    @PostMapping("/push")
    fun push(@RequestBody request: PushRequest): PushResponse =
        SyncHandlers.push(syncStore, request)

    @GetMapping("/pull")
    fun pull(
        @RequestParam(required = false) since: String?,
        @RequestParam(required = false) types: String?,
        @RequestParam(required = false) limit: String?,
        @RequestParam(required = false) cursor: String?,
    ): PullResponse {
        val params = parsePullQueryParams(since = since, types = types, limit = limit, cursor = cursor)
        return SyncHandlers.pull(syncStore, params)
    }
}