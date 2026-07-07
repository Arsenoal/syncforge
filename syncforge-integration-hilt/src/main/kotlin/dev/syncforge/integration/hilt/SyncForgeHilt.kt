package dev.syncforge.integration.hilt

import android.content.Context
import androidx.work.Configuration
import dev.syncforge.AndroidSyncForgeDsl
import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeAndroid
import dev.syncforge.android
import dev.syncforge.sync.SyncManager

/** Resolves a [SyncManager] for WorkManager from a Hilt singleton or lazy provider. */
fun interface SyncManagerProvider {
    fun get(): SyncManager
}

/**
 * Factory helpers for Hilt `@Provides` methods — copy patterns from RECIPES.md into your app module.
 *
 * SyncForge does not generate Hilt modules from KSP; wire [EntityRegistry] and DAOs in your `@Module`.
 */
object SyncForgeHilt {

    fun createSyncManager(
        context: Context,
        configure: AndroidSyncForgeDsl.() -> Unit,
    ): SyncManager = SyncForge.android(context.applicationContext, configure)

    fun workManagerConfiguration(
        syncManagerProvider: SyncManagerProvider,
    ): Configuration = SyncForgeAndroid.workManagerConfiguration { syncManagerProvider.get() }

    fun workManagerConfiguration(
        syncManagerProvider: () -> SyncManager,
    ): Configuration = workManagerConfiguration(SyncManagerProvider(syncManagerProvider))
}