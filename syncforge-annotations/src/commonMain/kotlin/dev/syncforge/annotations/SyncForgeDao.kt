package dev.syncforge.annotations

/**
 * Links a Room DAO to a [SyncForgeEntity] for KSP handler generation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SyncForgeDao(
    /** Fully-qualified name of the entity class, e.g. `com.example.TaskEntity`. */
    val entityClass: String,
)