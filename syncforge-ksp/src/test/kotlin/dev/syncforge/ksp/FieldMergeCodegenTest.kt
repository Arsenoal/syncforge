package dev.syncforge.ksp

import kotlin.test.Test
import kotlin.test.assertTrue

class FieldMergeCodegenTest {

    @Test
    fun generateSource_emitsLastWriteWinsObservedRemoveSetAndGrowOnlyCounter() {
        val source = FieldMergeCodegen.generateSource(
            packageName = "com.example.tasks",
            entityName = "TaskEntity",
            entityType = "tasks",
            fields = listOf(
                FieldMergeSpec("title", FieldMergeKind.LastWriteWins),
                FieldMergeSpec("tags", FieldMergeKind.ObservedRemoveSet),
                FieldMergeSpec("views", FieldMergeKind.GrowOnlyCounter),
            ),
        )

        assertTrue(source.contains("remoteUpdatedAtMillis >= local.updatedAtMillis) remote.title else local.title"))
        assertTrue(source.contains("(local.tags + remote.tags).distinct()"))
        assertTrue(source.contains("maxOf(local.views, remote.views)"))
        assertTrue(source.contains("object TaskEntityFieldMerge"))
        assertTrue(source.contains("entity(\"tasks\")"))
    }
}