package dev.syncforge.annotations

/**
 * Links an [dev.syncforge.entity.EntityStore] implementation to a [SyncForgeEntity] for KSP
 * handler generation.
 *
 * Example:
 * ```
 * @SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
 * class TaskEntityStore(...) : EntityStore<TaskEntity> { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SyncForgeStore(
    /** Fully-qualified name of the entity class, e.g. `com.example.tasks.TaskEntity`. */
    val entityClass: String,
)