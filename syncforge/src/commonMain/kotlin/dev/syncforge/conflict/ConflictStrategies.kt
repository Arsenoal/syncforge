package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity

object ConflictStrategies {

    fun lastWriteWins(): ConflictStrategy = LastWriteWinsStrategy()

    fun alwaysLocal(): ConflictStrategy = AlwaysLocalStrategy()

    fun alwaysRemote(): ConflictStrategy = AlwaysRemoteStrategy()

    fun deferToUser(): ConflictStrategy = DeferToUserStrategy()

    inline fun <reified T : SyncedEntity> merge(
        noinline block: MergeScope<T>.(local: T, remote: T) -> T,
    ): ConflictStrategy = MergeStrategy(block)
}