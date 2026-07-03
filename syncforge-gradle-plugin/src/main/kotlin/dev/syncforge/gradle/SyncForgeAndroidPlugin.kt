package dev.syncforge.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/**
 * Android app setup for SyncForge:
 * - Kotlin serialization plugin
 * - KSP with SyncForge handler codegen + Room compiler
 *
 * Runtime deps (Room, serialization, WorkManager) come transitively via [dev.syncforge:syncforge].
 */
class SyncForgeAndroidPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create<SyncForgeAndroidExtension>("syncForge")

        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val syncforgeGroup = project.findProperty("syncforge.group")?.toString()
            ?: DEFAULT_SYNCFORGE_GROUP
        val syncforgeVersion = project.findProperty("syncforge.version")?.toString()
            ?: DEFAULT_SYNCFORGE_VERSION
        val roomCompiler = project.roomCompilerFromCatalog()
            ?: "androidx.room:room-compiler:$DEFAULT_ROOM_VERSION"

        val kspProcessor = project.rootProject.findProject(":syncforge-ksp")
            ?: "$syncforgeGroup:syncforge-ksp:$syncforgeVersion"

        project.dependencies.apply {
            add("ksp", kspProcessor)
            add("ksp", roomCompiler)
        }
    }

    private fun Project.roomCompilerFromCatalog(): String? {
        val catalogs = extensions.findByType<VersionCatalogsExtension>() ?: return null
        val libs = runCatching { catalogs.named("libs") }.getOrNull() ?: return null
        return runCatching {
            val version = libs.findVersion("room").get().requiredVersion
            "androidx.room:room-compiler:$version"
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_SYNCFORGE_GROUP = "studio.syncforge"
        const val DEFAULT_SYNCFORGE_VERSION = "0.9.0-rc.1"
        const val DEFAULT_ROOM_VERSION = "2.7.1"
    }
}

/** Reserved for future version overrides via build logic. */
open class SyncForgeAndroidExtension {
    var syncforgeVersion: String? = null
    var roomCompilerCoordinate: String? = null
}