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
    val updatedAtMillis: Long? = null,
    val message: String? = null,
)

@Serializable
data class SimulateDeleteRequest(
    val entityType: String,
    val entityId: String,
)

@Serializable
data class SimulateDeleteResponse(
    val deleted: Boolean,
    val message: String? = null,
)