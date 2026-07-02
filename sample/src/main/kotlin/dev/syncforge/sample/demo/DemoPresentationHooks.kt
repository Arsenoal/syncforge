package dev.syncforge.sample.demo

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.compose.toUiModel
import dev.syncforge.debug.SyncEventType
import dev.syncforge.model.SyncStatus
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalSyncForgeApi::class)
fun CoroutineScope.attachDemoPresentation(
    syncManager: SyncManager,
    observeTaskCount: suspend () -> Int,
) {
    if (!DemoActivityLog.enabled) return

    DemoActivityLog.log(
        "App opened — SyncForge.android() ready; WorkManager periodic sync scheduled",
        highlight = true,
    )

    launch {
        val count = observeTaskCount()
        DemoActivityLog.log(
            "Room DB read — $count task(s) loaded from local SQLite (tasks table)",
            highlight = count == 0,
        )
        if (count == 0) {
            DemoActivityLog.setHighlight(
                "Local Room DB is empty — tap Sync to PULL remote data from mock-server",
            )
        }
    }

    launch {
        var previous: SyncStatus? = null
        syncManager.status.collect { status ->
            if (status == previous) return@collect
            previous = status
            val label = status.toUiModel().label
            when (status) {
                is SyncStatus.Syncing -> DemoActivityLog.log(
                    "Sync cycle running — phase: ${status.phase}",
                    highlight = true,
                )
                is SyncStatus.Offline -> DemoActivityLog.log(
                    "Offline — ${status.outboxCount} change(s) queued in SQLDelight outbox",
                    highlight = true,
                )
                is SyncStatus.Pending -> DemoActivityLog.log(
                    "Pending — ${status.outboxCount} change(s) waiting to push",
                    highlight = true,
                )
                is SyncStatus.Error -> DemoActivityLog.log(
                    "Sync error — ${status.message}",
                    highlight = true,
                )
                is SyncStatus.LastSynced -> DemoActivityLog.log(
                    "Last synced at ${status.timestampMillis} — $label",
                    highlight = true,
                )
                is SyncStatus.Idle -> DemoActivityLog.log("Status: $label")
            }
        }
    }

    launch {
        syncManager.debug.events
            .map { it.firstOrNull()?.summary to it.firstOrNull()?.type }
            .distinctUntilChanged()
            .collect { (summary, type) ->
                if (summary == null || type == null) return@collect
                val friendly = when (type) {
                    SyncEventType.FULL_SYNC -> "Full sync finished — $summary"
                    SyncEventType.PUSH -> "Push to server — $summary"
                    SyncEventType.PULL -> "Pull from server — $summary"
                    SyncEventType.ENQUEUE -> "Outbox enqueue — $summary"
                    SyncEventType.CONFLICT_RESOLVED -> "Conflict resolved — $summary"
                    SyncEventType.CONFLICT_OPENED -> "Conflict opened — $summary"
                    SyncEventType.OUTBOX_CLEARED -> "Outbox cleared — $summary"
                }
                DemoActivityLog.log(friendly, highlight = type == SyncEventType.PULL)
            }
    }
}

suspend fun logRoomTaskMutation(action: String, title: String) {
    DemoActivityLog.log(
        "Room UPDATE + outbox enqueue — $action task \"$title\" (optimistic local write)",
        highlight = true,
    )
}

fun logTaskListRefresh(count: Int) {
    DemoActivityLog.log("Room Flow emitted — UI shows $count task(s) from local DB")
}