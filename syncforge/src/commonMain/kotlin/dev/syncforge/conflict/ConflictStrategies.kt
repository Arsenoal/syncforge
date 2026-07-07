package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

object ConflictStrategies {

    /** Catalog aliases — use with `strategy(ConflictStrategies.acceptRemote)`. */
    val acceptLocal: ConflictStrategyKind = ConflictStrategyKind.ACCEPT_LOCAL
    val acceptRemote: ConflictStrategyKind = ConflictStrategyKind.ACCEPT_REMOTE

    fun lastWriteWins(): ConflictStrategy = LastWriteWinsStrategy()

    fun alwaysLocal(): ConflictStrategy = AlwaysLocalStrategy()

    fun alwaysRemote(): ConflictStrategy = AlwaysRemoteStrategy()

    fun deferToUser(): ConflictStrategy = DeferToUserStrategy()

    inline fun <reified T : SyncedEntity> merge(
        noinline block: MergeScope<T>.(local: T, remote: T) -> T,
    ): ConflictStrategy = MergeStrategy(block)

    /**
     * Resolves a catalog [kind] to a built-in [ConflictStrategy].
     *
     * @throws IllegalArgumentException for [ConflictStrategyKind.MERGE], [ConflictStrategyKind.GIT_LIKE],
     *   or [ConflictStrategyKind.CRDT] — those require configured DSL blocks.
     */
    fun fromKind(kind: ConflictStrategyKind): ConflictStrategy =
        when (kind) {
            ConflictStrategyKind.LAST_WRITE_WINS -> lastWriteWins()
            ConflictStrategyKind.ACCEPT_LOCAL -> alwaysLocal()
            ConflictStrategyKind.ACCEPT_REMOTE -> alwaysRemote()
            ConflictStrategyKind.DEFER_TO_USER -> deferToUser()
            ConflictStrategyKind.MERGE ->
                throw IllegalArgumentException(
                    "ConflictStrategyKind.MERGE requires merge { } — fromKind() cannot supply a merge block",
                )
            ConflictStrategyKind.GIT_LIKE ->
                throw IllegalArgumentException(
                    "ConflictStrategyKind.GIT_LIKE requires gitLike { } (not yet available)",
                )
            ConflictStrategyKind.CRDT ->
                throw IllegalArgumentException(
                    "ConflictStrategyKind.CRDT requires crdt { } (not yet available)",
                )
        }

    /**
     * Returns the catalog kind for built-in strategy instances created by this object.
     * Returns `null` for custom or not-yet-catalogued strategies (e.g. future `gitLike` / `crdt`).
     */
    fun kindOf(strategy: ConflictStrategy): ConflictStrategyKind? =
        when (strategy) {
            is LastWriteWinsStrategy -> ConflictStrategyKind.LAST_WRITE_WINS
            is AlwaysLocalStrategy -> ConflictStrategyKind.ACCEPT_LOCAL
            is AlwaysRemoteStrategy -> ConflictStrategyKind.ACCEPT_REMOTE
            is DeferToUserStrategy -> ConflictStrategyKind.DEFER_TO_USER
            is MergeStrategy<*> -> ConflictStrategyKind.MERGE
            else -> null
        }
}