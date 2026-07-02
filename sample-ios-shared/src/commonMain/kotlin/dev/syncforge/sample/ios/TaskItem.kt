package dev.syncforge.sample.ios

/**
 * Swift-friendly task row for the iOS sample UI.
 */
data class TaskItem(
    val id: String,
    val title: String,
    val completed: Boolean,
    val syncStateLabel: String,
)

internal fun SampleTaskEntity.toTaskItem(): TaskItem =
    TaskItem(
        id = id,
        title = title,
        completed = completed,
        syncStateLabel = syncState.name,
    )