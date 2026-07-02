package dev.syncforge.sample.tasks

import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
import dev.syncforge.sample.demo.DemoActivityLog
import dev.syncforge.sample.demo.logRoomTaskMutation
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskRepository(
    private val taskDao: TaskDao,
    private val syncManager: SyncManager,
) {

    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()

    suspend fun addTask(title: String) {
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            completed = false,
            localVersion = 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.create(TaskEntity.ENTITY_TYPE, task))
        logRoomTaskMutation("CREATE", task.title)
    }

    suspend fun toggleCompleted(task: TaskEntity) {
        val updated = task.copy(
            completed = !task.completed,
            localVersion = task.localVersion + 1,
            updatedAtMillis = System.currentTimeMillis(),
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.update(TaskEntity.ENTITY_TYPE, updated))
        logRoomTaskMutation("UPDATE", updated.title)
    }

    suspend fun deleteTask(task: TaskEntity) {
        syncManager.enqueueChange(
            Change.delete<TaskEntity>(
                entityType = TaskEntity.ENTITY_TYPE,
                entityId = task.id,
                localVersion = task.localVersion + 1,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        logRoomTaskMutation("DELETE", task.title)
    }

    suspend fun sync() {
        DemoActivityLog.log(
            "User tapped Sync — SyncManager.sync(): push outbox, then pull deltas from server",
            highlight = true,
        )
        syncManager.sync()
    }
}