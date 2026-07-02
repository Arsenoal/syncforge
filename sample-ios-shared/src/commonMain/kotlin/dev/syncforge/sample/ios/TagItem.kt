package dev.syncforge.sample.ios

/**
 * Swift-friendly tag row for the iOS sample UI.
 */
data class TagItem(
    val id: String,
    val label: String,
    val syncStateLabel: String,
)

internal fun SampleTagEntity.toTagItem(): TagItem =
    TagItem(
        id = id,
        label = label,
        syncStateLabel = syncState.name,
    )