package dev.syncforge.consumer.smoke

import android.app.Application
import dev.syncforge.SyncForge
import dev.syncforge.android
import dev.syncforge.consumer.smoke.tasks.ConsumerDatabase
import dev.syncforge.consumer.smoke.tasks.SyncForgeHandlers
import dev.syncforge.sync.SyncManager

/**
 * Minimal wiring used by [verifyConsumerSmoke] — depends only on published Maven artifacts.
 */
class ConsumerApplication : Application() {

    lateinit var syncManager: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()

        val taskDao = ConsumerDatabase.create(this).taskDao()

        syncManager = SyncForge.android(this) {
            baseUrl("https://api.example.com")
            registry(SyncForgeHandlers.registry(taskDao))
        }
    }
}