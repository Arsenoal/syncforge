package dev.syncforge.api

/**
 * Marks API that is public for early adopters but may change before SyncForge 1.0.
 *
 * Call sites must opt in explicitly:
 * ```
 * @OptIn(ExperimentalSyncForgeApi::class)
 * ```
 *
 * Or at module level in `build.gradle.kts`:
 * ```
 * kotlin {
 *     sourceSets.all {
 *         languageSettings.optIn("dev.syncforge.api.ExperimentalSyncForgeApi")
 *     }
 * }
 * ```
 */
@RequiresOptIn(
    message = "This SyncForge API is experimental and may change before 1.0.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalSyncForgeApi