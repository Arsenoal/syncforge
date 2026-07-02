package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.SyncedEntity

object ConflictStrategies {

    fun lastWriteWins(): ConflictStrategy = LastWriteWinsStrategy()

    fun alwaysLocal(): ConflictStrategy = AlwaysLocalStrategy()

    fun alwaysRemote(): ConflictStrategy = AlwaysRemoteStrategy()

    fun deferToUser(): ConflictStrategy = DeferToUserStrategy()

    inline fun <reified T : SyncedEntity> merge(
        noinline block: MergeScope<T>.(local: T, remote: T) -> T,
    ): ConflictStrategy = MergeStrategy(block)

    /** Adapts a legacy [ConflictResolver] to [ConflictStrategy]. */
    @Deprecated(
        message = "Migrate resolver logic to ConflictStrategies or a custom ConflictStrategy. Will be removed in 1.0.",
        replaceWith = ReplaceWith(
            expression = "ConflictStrategies.lastWriteWins()",
            imports = ["dev.syncforge.conflict.ConflictStrategies"],
        ),
    )
    @ExperimentalSyncForgeApi
    fun fromResolver(resolver: ConflictResolver): ConflictStrategy = LegacyResolverStrategy(resolver)
}