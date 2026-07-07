package dev.syncforge.network

/**
 * Default REST transport when [studio.syncforge:syncforge-network-ktor] is on the classpath.
 * Implemented in platform source sets via compile-only dependency on the Ktor adapter module.
 */
internal expect fun createDefaultKtorSyncTransport(baseUrl: String, auth: SyncAuthProvider?): SyncTransport