package dev.syncforge.api

import dev.syncforge.DesktopSyncForgeDsl
import dev.syncforge.conflict.ConflictStrategies
import dev.syncforge.network.SyncAuthProvider
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Stable JVM desktop entry points must not require [ExperimentalSyncForgeApi] opt-in (1.3-03).
 * This test class intentionally has no file-level opt-in.
 */
class StableDesktopApiSurfaceTest {

    @Test
    fun syncForgeDesktopEntryPointIsStable() {
        val desktopKt = Class.forName("dev.syncforge.SyncForgeDesktopKt")
        val desktopMethod = desktopKt.methods.single { it.name == "desktop" }
        assertNull(desktopMethod.getAnnotation(ExperimentalSyncForgeApi::class.java))
    }

    @Test
    fun desktopDslStableMethodsHaveNoExperimentalAnnotation() {
        val stable = setOf(
            "baseUrl",
            "authToken",
            "scope",
            "registry",
            "handler",
            "transport",
            "httpClient",
            "databaseName",
            "cursorStore",
            "networkMonitor",
            "networkMonitorAlwaysOnline",
            "conflicts",
            "getPullPageSize",
            "setPullPageSize",
            "getPushBatchSize",
            "setPushBatchSize",
            "getMaxRetries",
            "setMaxRetries",
            "getPeriodicSyncInterval",
            "setPeriodicSyncInterval",
        )
        for (method in DesktopSyncForgeDsl::class.java.methods) {
            if (method.name in stable) {
                assertNull(
                    method.getAnnotation(ExperimentalSyncForgeApi::class.java),
                    "Expected stable DesktopSyncForgeDsl member: ${method.name}",
                )
            }
        }
        val authProvider = DesktopSyncForgeDsl::class.java.methods
            .single { it.name == "auth" && it.parameterCount == 1 && it.parameterTypes[0] == SyncAuthProvider::class.java }
        assertNull(authProvider.getAnnotation(ExperimentalSyncForgeApi::class.java))
        val builtInAuth = DesktopSyncForgeDsl::class.java.methods
            .single { it.name == "auth" && it.parameterCount == 1 && it.parameterTypes[0] != SyncAuthProvider::class.java }
        assertNull(builtInAuth.getAnnotation(ExperimentalSyncForgeApi::class.java))
    }

    /** Compile-time: stable DSL configures without opt-in. */
    @Test
    fun stableDesktopDslConfiguresWithoutOptIn() {
        fun configure(block: DesktopSyncForgeDsl.() -> Unit) = block
        configure {
            baseUrl("http://localhost:8080")
            databaseName("syncforge-desktop.db")
            networkMonitorAlwaysOnline()
            authToken { null }
            auth {
                tokenFields(
                    accessToken = "access_token",
                    refreshToken = "refresh_token",
                    expiresInSeconds = "expires_in",
                )
            }
            conflicts {
                entity("tasks") { deferToUser() }
                default(ConflictStrategies.lastWriteWins())
            }
        }
    }
}