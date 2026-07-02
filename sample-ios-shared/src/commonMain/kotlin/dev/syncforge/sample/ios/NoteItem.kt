package dev.syncforge.sample.ios

/**
 * Swift-friendly note row for the iOS sample UI.
 */
data class NoteItem(
    val id: String,
    val title: String,
    val body: String,
    val syncStateLabel: String,
)

internal fun SampleNoteEntity.toNoteItem(): NoteItem =
    NoteItem(
        id = id,
        title = title,
        body = body,
        syncStateLabel = syncState.name,
    )