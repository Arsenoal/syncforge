package dev.syncforge.transport.firebase

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.api.toPullResult
import dev.syncforge.network.api.toPushResult
import dev.syncforge.transport.SyncDeltaStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * [SyncDeltaStore] backed by Firebase Firestore via Cloud Functions HTTPS endpoints.
 *
 * Deploy `firebase/functions`, then wire:
 *
 * ```
 * transport(DeltaStoreSyncTransport(FirebaseSyncDeltaStore(config)))
 * ```
 *
 * For background sync, attach a Firestore snapshot listener on `sync_entity` — see
 * [FirebaseListenerPatterns].
 */
@ExperimentalSyncForgeApi
class FirebaseSyncDeltaStore(
    private val api: FirebaseSyncApi,
    private val clock: () -> Long = { currentTimeMillis() },
) : SyncDeltaStore {

    constructor(
        config: FirebaseSyncConfig,
        httpClient: HttpClient = defaultHttpClient(),
    ) : this(FunctionsFirebaseSyncApi(config, httpClient))

    override suspend fun appendEntries(entries: List<OutboxEntry>): PushResult =
        api.push(entries, clock()).toPushResult()

    override suspend fun queryDeltas(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = api.pull(
        sinceTimestampMillis = sinceTimestampMillis,
        entityTypes = entityTypes,
        pageSize = pageSize,
        pageCursor = pageCursor,
        nowMillis = clock(),
    ).toPullResult()

    companion object {
        fun defaultHttpClient(
            json: Json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            },
        ): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}

internal expect fun currentTimeMillis(): Long