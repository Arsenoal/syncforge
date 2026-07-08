package dev.syncforge.sample.conflicts

import dev.syncforge.conflict.ConflictStrategyKind
import dev.syncforge.sample.notes.NoteEntity
import dev.syncforge.sample.tags.TagEntity
import dev.syncforge.sample.tasks.TaskEntity

/** Persisted per-entity conflict strategy selections for [:sample] settings (1.2-10). */
data class SampleEntityConflictKinds(
    val notes: ConflictStrategyKind = Defaults.notes,
    val tags: ConflictStrategyKind = Defaults.tags,
    val tasks: ConflictStrategyKind = Defaults.tasks,
) {
    fun kindFor(entityType: String): ConflictStrategyKind? = when (entityType) {
        NoteEntity.ENTITY_TYPE -> notes
        TagEntity.ENTITY_TYPE -> tags
        TaskEntity.ENTITY_TYPE -> tasks
        else -> null
    }

    fun withKind(entityType: String, kind: ConflictStrategyKind): SampleEntityConflictKinds =
        when (entityType) {
            NoteEntity.ENTITY_TYPE -> copy(notes = kind)
            TagEntity.ENTITY_TYPE -> copy(tags = kind)
            TaskEntity.ENTITY_TYPE -> copy(tasks = kind)
            else -> this
        }

    fun toEntityMap(): Map<String, ConflictStrategyKind> = mapOf(
        NoteEntity.ENTITY_TYPE to notes,
        TagEntity.ENTITY_TYPE to tags,
        TaskEntity.ENTITY_TYPE to tasks,
    )

    companion object {
        val Default: SampleEntityConflictKinds = SampleEntityConflictKinds()

        object Defaults {
            val notes: ConflictStrategyKind = ConflictStrategyKind.ACCEPT_REMOTE
            val tags: ConflictStrategyKind = ConflictStrategyKind.LAST_WRITE_WINS
            val tasks: ConflictStrategyKind = ConflictStrategyKind.GIT_LIKE
        }
    }
}