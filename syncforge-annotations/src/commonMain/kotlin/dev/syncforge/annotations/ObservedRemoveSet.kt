package dev.syncforge.annotations

/**
 * Marks a collection field for observed-remove set merge (union of local and remote elements).
 *
 * Supports [kotlin.collections.List] and [kotlin.collections.Set] value types.
 * Used by SyncForge KSP to generate [EntityName]FieldMerge helpers.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ObservedRemoveSet