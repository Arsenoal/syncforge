package dev.syncforge.api

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Guards macOS DSL graduation (1.3-03). macosMain does not compile on JVM — source annotation check.
 */
class StableMacosApiSurfaceSourceTest {

    @Test
    fun macosDslEntryPointIsStableInSource() {
        val source = readModuleSource("macosMain/kotlin/dev/syncforge/SyncForgeMacos.kt")
        assertFalse(
            source.contains("@ExperimentalSyncForgeApi\nfun SyncForge.macos"),
            "SyncForge.macos should not require ExperimentalSyncForgeApi",
        )
    }

    private fun readModuleSource(relativePath: String): String {
        val candidates = listOf(
            File("src/$relativePath"),
            File("../syncforge/src/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("Missing source at src/$relativePath (cwd=${File(".").absolutePath})")
    }
}