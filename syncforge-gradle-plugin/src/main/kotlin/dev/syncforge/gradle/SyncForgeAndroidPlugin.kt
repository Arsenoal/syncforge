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
 * Runtime deps (Room, serialization, WorkManager) come transitively via [studio.syncforge:syncforge].
 */
class SyncForgeAndroidPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create<SyncForgeAndroidExtension>("syncForge")

        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val syncforgeGroup = project.findProperty("syncforge.group")?.toString()
            ?: DEFAULT_SYNCFORGE_GROUP
        val syncforgeVersion = project.findProperty("syncforge.version")?.toString()
            ?: project.syncforgeVersionFromCatalog()
            ?: DEFAULT_SYNCFORGE_VERSION
        val roomCompiler = project.roomCompilerFromCatalog()
            ?: "androidx.room:room-compiler:$DEFAULT_ROOM_VERSION"

        val kspProcessor = project.rootProject.findProject(":syncforge-ksp")
            ?: "$syncforgeGroup:syncforge-ksp:$syncforgeVersion"

        val networkKtor = "$syncforgeGroup:syncforge-network-ktor:$syncforgeVersion"

        project.dependencies.apply {
            add("implementation", networkKtor)
            add("ksp", kspProcessor)
            add("ksp", roomCompiler)
        }
    }

    private fun Project.roomCompilerFromCatalog(): String? {
        val libs = versionCatalogLibs() ?: return null
        return runCatching {
            val version = libs.findVersion("room").get().requiredVersion
            "androidx.room:room-compiler:$version"
        }.getOrNull()
    }

    private fun Project.syncforgeVersionFromCatalog(): String? {
        val libs = versionCatalogLibs() ?: return null
        return runCatching { libs.findVersion("syncforge").get().requiredVersion }.getOrNull()
    }

    private fun Project.versionCatalogLibs() =
        extensions.findByType<VersionCatalogsExtension>()
            ?.let { runCatching { it.named("libs") }.getOrNull() }

    companion object {
        const val DEFAULT_SYNCFORGE_GROUP = "studio.syncforge"
        const val DEFAULT_SYNCFORGE_VERSION = "1.0.0"
        const val DEFAULT_ROOM_VERSION = "2.7.1"
    }
}

/** Reserved for future version overrides via build logic. */
open class SyncForgeAndroidExtension {
    var syncforgeVersion: String? = null
    var roomCompilerCoordinate: String? = null
}