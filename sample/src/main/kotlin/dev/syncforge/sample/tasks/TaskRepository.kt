package dev.syncforge.sample.tasks

import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
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
    }

    suspend fun toggleCompleted(task: TaskEntity) {
        val updated = task.copy(
            completed = !task.completed,
            localVersion = task.localVersion + 1,
            updatedAtMillis = System.currentTimeMillis(),
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.update(TaskEntity.ENTITY_TYPE, updated))
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
    }

    suspend fun sync() = syncManager.sync()
}