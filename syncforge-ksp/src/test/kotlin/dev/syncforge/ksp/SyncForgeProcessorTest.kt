package dev.syncforge.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class SyncForgeProcessorTest {

    @Test
    fun generatesEntityStoreHandler() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(entitySource, storeSource)
            symbolProcessorProviders = listOf(SyncForgeProcessorProvider())
            inheritClassPath = true
            messageOutputStream = System.out
        }

        val result = compilation.compile()
        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            System.err.println(result.messages)
        }

        val handler = generatedFile(compilation, "TaskEntitySyncHandler.kt")
        assertTrue(handler.contains("EntityStoreSyncHandler<TaskEntity>"))
        assertTrue(handler.contains("store: com.example.tasks.TaskEntityStore"))

        val registry = generatedFile(compilation, "SyncForgeHandlers.kt")
        assertTrue(registry.contains("fun tasks(taskEntityStore: com.example.tasks.TaskEntityStore)"))
        assertTrue(registry.contains("EntityRegistry.of(tasks(taskEntityStore))"))
    }

    @Test
    fun generatesFieldMergeWhenPropertiesAnnotated() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(annotatedEntitySource, storeSource)
            symbolProcessorProviders = listOf(SyncForgeProcessorProvider())
            inheritClassPath = true
            messageOutputStream = System.out
        }

        val result = compilation.compile()
        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            System.err.println(result.messages)
        }

        val merge = generatedFile(compilation, "TaskEntityFieldMerge.kt")
        assertTrue(merge.contains("object TaskEntityFieldMerge"))
        assertTrue(merge.contains("remote.title else local.title"))
        assertTrue(merge.contains("(local.tags + remote.tags).distinct()"))
        assertTrue(merge.contains("maxOf(local.views, remote.views)"))
    }

    @Test
    fun daoPath_unchanged() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(entitySource, daoSource)
            symbolProcessorProviders = listOf(SyncForgeProcessorProvider())
            inheritClassPath = true
            messageOutputStream = System.out
        }

        val result = compilation.compile()
        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            System.err.println(result.messages)
        }

        val handler = generatedFile(compilation, "TaskEntitySyncHandler.kt")
        assertTrue(handler.contains("TypedEntitySyncHandler<TaskEntity>"))
        assertTrue(handler.contains("private val dao: com.example.tasks.TaskDao"))
    }

    private fun generatedFile(compilation: KotlinCompilation, name: String): String {
        val file = compilation.kspSourcesDir.walkTopDown().first { it.name == name }
        return file.readText()
    }

    private val annotatedEntitySource = SourceFile.kotlin(
        "TaskEntity.kt",
        """
        package com.example.tasks

        import dev.syncforge.annotations.GrowOnlyCounter
        import dev.syncforge.annotations.LastWriteWins
        import dev.syncforge.annotations.ObservedRemoveSet
        import dev.syncforge.annotations.SyncForgeEntity
        import dev.syncforge.entity.SyncedEntity
        import dev.syncforge.model.SyncState
        import kotlinx.serialization.Serializable

        @SyncForgeEntity(entityType = "tasks")
        @Serializable
        data class TaskEntity(
            override val id: String,
            @LastWriteWins val title: String,
            @ObservedRemoveSet val tags: List<String> = emptyList(),
            @GrowOnlyCounter val views: Int = 0,
            override val localVersion: Long = 0,
            override val updatedAtMillis: Long = 0,
            override val syncState: SyncState = SyncState.SYNCED,
        ) : SyncedEntity
        """.trimIndent(),
    )

    private val entitySource = SourceFile.kotlin(
        "TaskEntity.kt",
        """
        package com.example.tasks

        import dev.syncforge.annotations.SyncForgeEntity
        import dev.syncforge.entity.SyncedEntity
        import dev.syncforge.model.SyncState
        import kotlinx.serialization.Serializable

        @SyncForgeEntity(entityType = "tasks")
        @Serializable
        data class TaskEntity(
            override val id: String,
            val title: String,
            override val localVersion: Long = 0,
            override val updatedAtMillis: Long = 0,
            override val syncState: SyncState = SyncState.SYNCED,
        ) : SyncedEntity
        """.trimIndent(),
    )

    private val storeSource = SourceFile.kotlin(
        "TaskEntityStore.kt",
        """
        package com.example.tasks

        import dev.syncforge.annotations.SyncForgeStore
        import dev.syncforge.entity.EntityStore

        @SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
        class TaskEntityStore : EntityStore<TaskEntity> {
            private val rows = linkedMapOf<String, TaskEntity>()

            override suspend fun findById(id: String): TaskEntity? = rows[id]

            override suspend fun upsert(entity: TaskEntity) {
                rows[entity.id] = entity
            }

            override suspend fun delete(id: String) {
                rows.remove(id)
            }
        }
        """.trimIndent(),
    )

    private val daoSource = SourceFile.kotlin(
        "TaskDao.kt",
        """
        package com.example.tasks

        import dev.syncforge.annotations.SyncForgeDao

        @SyncForgeDao(entityClass = "com.example.tasks.TaskEntity")
        interface TaskDao {
            suspend fun findById(id: String): TaskEntity?
            suspend fun insert(entity: TaskEntity)
            suspend fun update(entity: TaskEntity)
            suspend fun deleteById(id: String)
        }
        """.trimIndent(),
    )
}