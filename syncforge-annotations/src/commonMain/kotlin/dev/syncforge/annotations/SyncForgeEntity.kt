package dev.syncforge.annotations

/**
 * Marks a Room entity for SyncForge KSP handler generation.
 *
 * The entity must implement [dev.syncforge.entity.SyncedEntity] and be
 * [kotlinx.serialization.Serializable].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SyncForgeEntity(
    val entityType: String,
)