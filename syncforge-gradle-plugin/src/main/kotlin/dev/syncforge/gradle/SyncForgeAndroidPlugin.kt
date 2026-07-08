package dev.syncforge.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/**
 * Android app setup for SyncForge:
 * - Kotlin serialization plugin
 * - KSP with SyncForge handler codegen
 * - Room compiler on KSP only when Room is used (or [SyncForgeAndroidExtension.roomCodegen] is true)
 *
 * Runtime deps (Room, serialization, WorkManager) come transitively via [studio.syncforge:syncforge]
 * when you use Room. BYO-store apps can opt out of Room KSP — see GETTING_STARTED.
 */
class SyncForgeAndroidPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create<SyncForgeAndroidExtension>("syncForge")

        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val syncforgeGroup = project.findProperty("syncforge.group")?.toString()
            ?: DEFAULT_SYNCFORGE_GROUP
        val syncforgeVersion = project.findProperty("syncforge.version")?.toString()
            ?: project.syncforgeVersionFromCatalog()
            ?: DEFAULT_SYNCFORGE_VERSION
        val roomCompiler = extension.roomCompilerCoordinate
            ?: project.roomCompilerFromCatalog()
            ?: "androidx.room:room-compiler:$DEFAULT_ROOM_VERSION"

        val kspProcessor = project.rootProject.findProject(":syncforge-ksp")
            ?: "$syncforgeGroup:syncforge-ksp:$syncforgeVersion"

        val networkKtor = "$syncforgeGroup:syncforge-network-ktor:$syncforgeVersion"

        project.dependencies.apply {
            add("implementation", networkKtor)
            add("ksp", kspProcessor)
        }

        project.afterEvaluate {
            if (extension.shouldApplyRoomCodegen(project)) {
                project.dependencies.add("ksp", roomCompiler)
                project.logger.lifecycle(
                    "studio.syncforge.android: applying Room KSP compiler",
                )
            } else {
                project.logger.lifecycle(
                    "studio.syncforge.android: skipping Room KSP compiler " +
                        "(no Room usage detected; syncForge { roomCodegen = true } to force)",
                )
            }
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
        const val DEFAULT_SYNCFORGE_VERSION = "1.2.0"
        const val DEFAULT_ROOM_VERSION = "2.7.1"
    }
}

private val roomSourceMarkers = listOf(
    "@SyncForgeDao",
    "androidx.room.",
)

private val roomSourceRoots = listOf(
    "src/main/kotlin",
    "src/main/java",
    "src/androidMain/kotlin",
    "src/androidMain/java",
)

/** Reserved for future version overrides via build logic. */
open class SyncForgeAndroidExtension {
    var syncforgeVersion: String? = null
    var roomCompilerCoordinate: String? = null

    /**
     * When `true`, adds Room KSP compiler. When `false`, skips it (BYO store without Room).
     * Default `null`: auto-detect from sources (`@SyncForgeDao` or `androidx.room` imports).
     */
    var roomCodegen: Boolean? = null
}

internal fun SyncForgeAndroidExtension.shouldApplyRoomCodegen(project: Project): Boolean {
    roomCodegen?.let { return it }
    return project.detectsRoomUsage()
}

internal fun Project.detectsRoomUsage(): Boolean {
    val markers = roomSourceMarkers
    val roots = roomSourceRoots
    return roots.any { root ->
        val dir = file(root)
        if (!dir.isDirectory) return@any false
        dir.walkTopDown()
            .asSequence()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .any { file ->
                val text = runCatching { file.readText() }.getOrNull() ?: return@any false
                markers.any(text::contains)
            }
    }
}