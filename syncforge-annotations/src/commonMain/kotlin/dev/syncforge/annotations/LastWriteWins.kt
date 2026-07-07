package dev.syncforge.annotations

/**
 * Marks an entity field for last-write-wins merge (newer [dev.syncforge.entity.SyncedEntity.updatedAtMillis] wins).
 *
 * Used by SyncForge KSP to generate [EntityName]FieldMerge helpers for `merge { }` conflict strategies.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class LastWriteWins