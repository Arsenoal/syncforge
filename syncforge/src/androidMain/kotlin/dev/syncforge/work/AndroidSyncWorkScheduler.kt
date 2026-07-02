package dev.syncforge.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.syncforge.sync.SyncWorkScheduler
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

internal class AndroidSyncWorkScheduler(
    private val context: Context,
    private val workName: String = "syncforge_periodic_sync",
    private val retryWorkName: String = "syncforge_retry_sync",
) : SyncWorkScheduler {

    override fun schedulePeriodic(interval: Duration) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            interval.inWholeMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun scheduleRetry(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            retryWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(workName)
        workManager.cancelUniqueWork(retryWorkName)
    }
}