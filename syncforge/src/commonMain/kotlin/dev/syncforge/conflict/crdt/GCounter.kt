package dev.syncforge.conflict.crdt

import kotlinx.serialization.Serializable

/**
 * Grow-only counter — per-replica counts merge with pointwise max, then sum.
 */
@Serializable
data class GCounter(
    val counts: Map<String, Int> = emptyMap(),
) {
    fun increment(replicaId: String, delta: Int = 1): GCounter {
        require(delta >= 0) { "GCounter only supports non-negative increments" }
        val current = counts[replicaId] ?: 0
        return copy(counts = counts + (replicaId to current + delta))
    }

    fun value(): Int = counts.values.sum()

    fun merge(other: GCounter): GCounter {
        val replicaIds = counts.keys + other.counts.keys
        return GCounter(
            counts = replicaIds.associateWith { id ->
                maxOf(counts[id] ?: 0, other.counts[id] ?: 0)
            },
        )
    }

    companion object {
        fun zero(): GCounter = GCounter()
    }
}