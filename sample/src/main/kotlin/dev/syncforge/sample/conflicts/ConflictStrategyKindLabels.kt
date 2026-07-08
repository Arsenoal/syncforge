package dev.syncforge.sample.conflicts

import dev.syncforge.conflict.ConflictStrategyKind

fun ConflictStrategyKind.displayLabel(): String = when (this) {
    ConflictStrategyKind.ACCEPT_LOCAL -> "Accept local"
    ConflictStrategyKind.ACCEPT_REMOTE -> "Accept remote"
    ConflictStrategyKind.DEFER_TO_USER -> "Defer to user"
    ConflictStrategyKind.LAST_WRITE_WINS -> "Last write wins"
    ConflictStrategyKind.GIT_LIKE -> "Git-like (three-way)"
    ConflictStrategyKind.MERGE -> "Merge (configured)"
    ConflictStrategyKind.CRDT -> "CRDT (configured)"
}

/** Kinds offered in the settings UI per entity (MERGE/CRDT need static DSL blocks). */
object ConflictSettingsCatalog {

    val notesAndTags: List<ConflictStrategyKind> = listOf(
        ConflictStrategyKind.ACCEPT_REMOTE,
        ConflictStrategyKind.LAST_WRITE_WINS,
        ConflictStrategyKind.DEFER_TO_USER,
        ConflictStrategyKind.ACCEPT_LOCAL,
    )

    val tasks: List<ConflictStrategyKind> = listOf(
        ConflictStrategyKind.GIT_LIKE,
        ConflictStrategyKind.DEFER_TO_USER,
        ConflictStrategyKind.LAST_WRITE_WINS,
        ConflictStrategyKind.ACCEPT_REMOTE,
        ConflictStrategyKind.ACCEPT_LOCAL,
    )
}