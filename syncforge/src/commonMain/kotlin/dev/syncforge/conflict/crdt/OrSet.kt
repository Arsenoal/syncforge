package dev.syncforge.conflict.crdt

import kotlinx.serialization.Serializable

/**
 * Observed-remove set — additive merges union element tags; removes are tombstoned by tag.
 */
@Serializable
data class OrSet<T>(
    private val adds: Map<String, T> = emptyMap(),
    private val removes: Set<String> = emptySet(),
) {
    fun add(value: T, tag: String = newTag()): OrSet<T> =
        copy(adds = adds + (tag to value))

    fun remove(value: T): OrSet<T> {
        val tagsToRemove = adds.filterValues { it == value }.keys
        if (tagsToRemove.isEmpty()) return this
        return copy(removes = removes + tagsToRemove)
    }

    fun merge(other: OrSet<T>): OrSet<T> =
        OrSet(
            adds = adds + other.adds,
            removes = removes + other.removes,
        )

    fun elements(): Set<T> =
        adds.filterKeys { it !in removes }.values.toSet()

    fun contains(value: T): Boolean = elements().contains(value)

    companion object {
        fun <T> fromCollection(items: Collection<T>): OrSet<T> =
            items.fold(OrSet()) { set, item -> set.add(item) }

        fun newTag(): String =
            "tag-${dev.syncforge.sync.currentTimeMillis()}-${kotlin.random.Random.nextLong()}"
    }
}