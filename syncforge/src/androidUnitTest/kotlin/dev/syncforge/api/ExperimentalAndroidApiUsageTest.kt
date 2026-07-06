package dev.syncforge.api

import dev.syncforge.AndroidSyncForgeDsl
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Compile-time guard: experimental Android DSL members require opt-in. */
@OptIn(ExperimentalSyncForgeApi::class)
class ExperimentalAndroidApiUsageTest {

    @Test
    fun experimentalDslMembersCompileWithOptIn() {
        val configure: AndroidSyncForgeDsl.() -> Unit = {
            customize { }
        }
        assertNotNull(configure)
    }
}