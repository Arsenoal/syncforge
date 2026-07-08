package dev.syncforge.api

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards iOS DSL graduation (1.3-02): [dev.syncforge.ios] and BGTask helpers are stable.
 *
 * appleMain does not compile on JVM, so this test asserts source annotations instead of reflection.
 */
class StableIosApiSurfaceSourceTest {

    @Test
    fun iosDslEntryPointIsStableInSource() {
        val source = readModuleSource("appleMain/kotlin/dev/syncforge/SyncForgeIos.kt")
        assertFalse(
            source.contains("@ExperimentalSyncForgeApi\nfun SyncForge.ios"),
            "SyncForge.ios should not require ExperimentalSyncForgeApi",
        )
        assertFalse(
            source.contains("@ExperimentalSyncForgeApi\nclass IosSyncForgeDsl"),
            "IosSyncForgeDsl should not be annotated experimental",
        )
        assertTrue(
            source.contains("@ExperimentalSyncForgeApi\n    fun persistence"),
            "persistence() should remain experimental (custom SQLDelight wiring)",
        )
        assertTrue(
            source.contains("@ExperimentalSyncForgeApi\n    fun customize"),
            "customize() should remain experimental (SyncForgeBuilder escape hatch)",
        )
    }

    @Test
    fun iosBackgroundSyncHelpersAreStableInSource() {
        val source = readModuleSource("iosMain/kotlin/dev/syncforge/work/IosBackgroundSync.kt")
        assertFalse(
            source.contains("@ExperimentalSyncForgeApi\nobject IosBackgroundSync"),
            "IosBackgroundSync should be stable",
        )
        assertFalse(
            source.contains("@ExperimentalSyncForgeApi\nfun registerIosBackgroundSyncTasks"),
            "registerIosBackgroundSyncTasks should be stable",
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