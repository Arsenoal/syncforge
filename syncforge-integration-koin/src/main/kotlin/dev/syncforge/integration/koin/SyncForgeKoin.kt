package dev.syncforge.integration.koin

import android.content.Context
import androidx.work.Configuration
import dev.syncforge.AndroidSyncForgeDsl
import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeAndroid
import dev.syncforge.android
import dev.syncforge.sync.SyncManager
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Resolves a [SyncManager] for WorkManager — typically `{ getKoin().get() }`. */
fun interface SyncManagerProvider {
    fun get(): SyncManager
}

/**
 * Koin module that registers a singleton [SyncManager] via [SyncForge.android].
 *
 * Requires `startKoin { androidContext(app) }`. When the registry needs other Koin beans (DAOs),
 * define `single<SyncManager>` manually and call `get()` inside the DSL block — see RECIPES.md.
 */
fun syncForgeModule(
    configure: AndroidSyncForgeDsl.() -> Unit,
): Module = module {
    single<SyncManager> {
        SyncForge.android(get<Context>(), configure)
    }
}

/** WorkManager [Configuration] wired to a [SyncManager] from Koin (default) or a custom provider. */
fun syncForgeWorkManagerConfiguration(
    syncManagerProvider: SyncManagerProvider = SyncManagerProvider {
        GlobalContext.get().get<SyncManager>()
    },
): Configuration = SyncForgeAndroid.workManagerConfiguration { syncManagerProvider.get() }