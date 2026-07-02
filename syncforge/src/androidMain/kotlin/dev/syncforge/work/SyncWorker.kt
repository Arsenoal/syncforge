package dev.syncforge.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.syncforge.sync.SyncManager

/**
 * WorkManager worker that delegates to an app-provided [SyncManager].
 *
 * Wire via Hilt/Koin/manual factory in the Application class (Phase 2 sample app).
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncManagerProvider: () -> SyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (val outcome = syncManagerProvider().sync()) {
            is dev.syncforge.model.SyncResult.Failure -> {
                if (outcome.error.code == dev.syncforge.model.SyncError.Code.NETWORK) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
            else -> Result.success()
        }
    }
}