package dev.syncforge.annotations

/**
 * Marks a numeric counter field for grow-only merge ([kotlin.math.max] of local and remote).
 *
 * Supports [Int] and [Long]. Used by SyncForge KSP to generate [EntityName]FieldMerge helpers.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class GrowOnlyCounter