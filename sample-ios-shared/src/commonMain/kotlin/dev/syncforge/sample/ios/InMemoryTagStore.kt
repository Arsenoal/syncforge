package dev.syncforge.sample.ios

import dev.syncforge.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTagStore {

    private val mutex = Mutex()
    private val tags = MutableStateFlow<List<SampleTagEntity>>(emptyList())

    fun observeAll(): Flow<List<SampleTagEntity>> = tags.asStateFlow()

    fun snapshotAll(): List<SampleTagEntity> = tags.value

    suspend fun createTag(label: String, nowMillis: Long): SampleTagEntity = mutex.withLock {
        val tag = SampleTagEntity(
            id = randomSampleId(),
            label = label.trim(),
            localVersion = 1,
            updatedAtMillis = nowMillis,
            syncState = SyncState.PENDING,
        )
        tags.value = listOf(tag) + tags.value
        tag
    }

    suspend fun findById(id: String): SampleTagEntity? = mutex.withLock {
        tags.value.firstOrNull { it.id == id }
    }

    suspend fun insert(tag: SampleTagEntity) = mutex.withLock {
        tags.value = listOf(tag) + tags.value.filter { it.id != tag.id }
    }

    suspend fun update(tag: SampleTagEntity) = mutex.withLock {
        tags.value = tags.value.map { if (it.id == tag.id) tag else it }
    }

    suspend fun deleteById(id: String) = mutex.withLock {
        tags.value = tags.value.filter { it.id != id }
    }
}