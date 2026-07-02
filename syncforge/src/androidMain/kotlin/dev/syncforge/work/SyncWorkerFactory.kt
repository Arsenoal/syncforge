package dev.syncforge.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.syncforge.sync.SyncManager

class SyncWorkerFactory(
    private val syncManagerProvider: () -> SyncManager,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != SyncWorker::class.java.name) return null
        return SyncWorker(appContext, workerParameters, syncManagerProvider)
    }
}