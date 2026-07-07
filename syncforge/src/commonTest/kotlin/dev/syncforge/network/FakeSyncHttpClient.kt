package dev.syncforge.network

import dev.syncforge.network.api.PushRequest

/**
 * Test double for [SyncHttpClient] — records calls and returns configured results.
 */
class FakeSyncHttpClient(
    private var pushResult: PushResult = PushResult(acknowledgedIds = emptyList()),
    private var pullResult: PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = 0L),
) : SyncHttpClient {

    data class RecordedPush(val baseUrl: String, val request: PushRequest)
    data class RecordedPull(val baseUrl: String, val params: PullQueryParams)

    val pushCalls = mutableListOf<RecordedPush>()
    val pullCalls = mutableListOf<RecordedPull>()

    fun setPushResult(result: PushResult) {
        pushResult = result
    }

    fun setPullResult(result: PullResult) {
        pullResult = result
    }

    override suspend fun postPush(baseUrl: String, request: PushRequest): PushResult {
        pushCalls += RecordedPush(baseUrl, request)
        return pushResult
    }

    override suspend fun getPull(baseUrl: String, params: PullQueryParams): PullResult {
        pullCalls += RecordedPull(baseUrl, params)
        return pullResult
    }
}