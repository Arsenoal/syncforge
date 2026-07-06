package dev.syncforge.api

/**
 * Marks API that is public but not covered by SyncForge's semver-stable contract.
 *
 * **Stable (no annotation):** [dev.syncforge.SyncForge.android], [dev.syncforge.SyncForgeAndroid],
 * core [dev.syncforge.sync.SyncManager] (sync lifecycle, outbox, conflicts — not auth/debug),
 * [dev.syncforge.conflict.ConflictPolicy] / [dev.syncforge.conflict.ConflictStrategies],
 * [dev.syncforge.compose.SyncStatusUiModel], and production conflict/status Compose helpers.
 *
 * Experimental APIs may change in minor releases until explicitly graduated.
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
    message = "This SyncForge API is experimental and may change in a future release.",
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