package dev.syncforge.mockserver

import kotlinx.serialization.Serializable

@Serializable
data class SimulateEditRequest(
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
)

@Serializable
data class SimulateEditResponse(
    val updated: Boolean,
    val message: String? = null,
)