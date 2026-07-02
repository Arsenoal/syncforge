package dev.syncforge.sample.ios

import dev.syncforge.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory task storage for the iOS sample — consumer apps use Room/GRDB on each platform.
 */
class InMemoryTaskStore {

    private val mutex = Mutex()
    private val tasks = MutableStateFlow<List<SampleTaskEntity>>(emptyList())

    fun observeAll(): Flow<List<SampleTaskEntity>> = tasks.asStateFlow()

    fun snapshotAll(): List<SampleTaskEntity> = tasks.value

    suspend fun createTask(title: String, nowMillis: Long): SampleTaskEntity = mutex.withLock {
        val task = SampleTaskEntity(
            id = randomSampleId(),
            title = title.trim(),
            completed = false,
            localVersion = 1,
            updatedAtMillis = nowMillis,
            syncState = SyncState.PENDING,
        )
        tasks.value = listOf(task) + tasks.value
        task
    }

    suspend fun findById(id: String): SampleTaskEntity? = mutex.withLock {
        tasks.value.firstOrNull { it.id == id }
    }

    suspend fun insert(task: SampleTaskEntity) = mutex.withLock {
        tasks.value = listOf(task) + tasks.value.filter { it.id != task.id }
    }

    suspend fun update(task: SampleTaskEntity) = mutex.withLock {
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    suspend fun deleteById(id: String) = mutex.withLock {
        tasks.value = tasks.value.filter { it.id != id }
    }
}