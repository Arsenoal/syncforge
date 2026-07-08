package dev.syncforge.mockserver

import kotlinx.serialization.Serializable

@Serializable
data class E2eSessionCreateRequest(
    val sessionId: String,
)

@Serializable
data class E2eSessionCreateResponse(
    val created: Boolean,
)

@Serializable
data class E2eSessionPutRequest(
    val key: String,
    val value: String,
)

@Serializable
data class E2eSessionPutResponse(
    val stored: Boolean,
)

@Serializable
data class E2eSessionSnapshotResponse(
    val values: Map<String, String> = emptyMap(),
)