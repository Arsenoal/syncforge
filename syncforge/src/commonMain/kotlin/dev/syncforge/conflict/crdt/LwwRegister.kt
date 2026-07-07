package dev.syncforge.conflict.crdt

import kotlinx.serialization.Serializable

/**
 * Last-write-wins register — a CRDT for a single scalar value.
 *
 * Conflicts resolve by [timestamp]; [nodeId] breaks ties deterministically.
 */
@Serializable
data class LwwRegister<T>(
    val value: T,
    val timestamp: Long,
    val nodeId: String = "",
) {
    fun assign(value: T, timestamp: Long, nodeId: String = this.nodeId): LwwRegister<T> =
        copy(value = value, timestamp = timestamp, nodeId = nodeId)

    fun merge(other: LwwRegister<T>): LwwRegister<T> =
        when {
            other.timestamp > timestamp -> other
            other.timestamp < timestamp -> this
            other.nodeId > nodeId -> other
            else -> this
        }

    companion object {
        fun <T> of(value: T, timestamp: Long, nodeId: String = ""): LwwRegister<T> =
            LwwRegister(value, timestamp, nodeId)
    }
}