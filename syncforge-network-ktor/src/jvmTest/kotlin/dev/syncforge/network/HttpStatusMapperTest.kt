package dev.syncforge.network

import dev.syncforge.model.SyncError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
class HttpStatusMapperTest {

    @Test
    fun http429_mapsToRetryableServerError() {
        val error = HttpStatusMapper.toSyncError(HttpStatusCode.TooManyRequests, body = "slow down")
        assertEquals(SyncError.Code.SERVER, error.code)
        assertEquals(429, error.httpStatus)
    }

    @Test
    fun retryAfterHeader_parsesDelaySeconds() {
        val error = HttpStatusMapper.toSyncError(
            status = HttpStatusCode.TooManyRequests,
            body = "rate limited",
            retryAfterHeader = "120",
        )
        assertEquals(120_000L, error.retryAfterMillis)
    }

    @Test
    fun parseRetryAfterMillis_returnsNullForBlank() {
        assertEquals(null, HttpStatusMapper.parseRetryAfterMillis(null))
        assertEquals(null, HttpStatusMapper.parseRetryAfterMillis("  "))
    }
}