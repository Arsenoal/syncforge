package dev.syncforge.api

import dev.syncforge.AndroidSyncForgeDsl
import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeAndroid
import dev.syncforge.conflict.ConflictStrategies
import dev.syncforge.network.SyncAuthProvider
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stable Android entry points must not require [ExperimentalSyncForgeApi] opt-in.
 * This test class intentionally has no file-level opt-in.
 */
class StableAndroidApiSurfaceTest {

    @Test
    fun syncForgeAndroidEntryPointIsStable() {
        val androidKt = Class.forName("dev.syncforge.SyncForgeAndroidKt")
        val androidMethod = androidKt.methods.single { it.name == "android" }
        assertNull(androidMethod.getAnnotation(ExperimentalSyncForgeApi::class.java))
        assertNull(
            SyncForgeAndroid::class.java
                .getMethod("workManagerConfiguration", kotlin.jvm.functions.Function0::class.java)
                .getAnnotation(ExperimentalSyncForgeApi::class.java),
        )
    }

    @Test
    fun androidDslStableMethodsHaveNoExperimentalAnnotation() {
        val stable = setOf(
            "baseUrl",
            "authToken",
            "scope",
            "registry",
            "handler",
            "transport",
            "databaseName",
            "schedulePeriodicSyncOnStart",
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
        for (method in AndroidSyncForgeDsl::class.java.methods) {
            if (method.name in stable) {
                assertNull(
                    method.getAnnotation(ExperimentalSyncForgeApi::class.java),
                    "Expected stable AndroidSyncForgeDsl member: ${method.name}",
                )
            }
        }
        val authProvider = AndroidSyncForgeDsl::class.java.methods
            .single { it.name == "auth" && it.parameterCount == 1 && it.parameterTypes[0] == SyncAuthProvider::class.java }
        assertNull(authProvider.getAnnotation(ExperimentalSyncForgeApi::class.java))
    }

    /** Compile-time: stable DSL configures without opt-in. */
    @Test
    fun stableAndroidDslConfiguresWithoutOptIn() {
        fun configure(block: AndroidSyncForgeDsl.() -> Unit) = block
        configure {
            baseUrl("https://api.example.com")
            databaseName("syncforge.db")
            schedulePeriodicSyncOnStart(false)
            authToken { null }
            conflicts {
                entity("tasks") { deferToUser() }
                default(ConflictStrategies.lastWriteWins())
            }
        }
    }

}