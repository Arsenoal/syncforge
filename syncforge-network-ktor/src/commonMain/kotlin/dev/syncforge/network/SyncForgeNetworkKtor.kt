package dev.syncforge.network

/**
 * Ensures the Ktor adapter registers with core before [dev.syncforge.network.createKtorSyncTransport]
 * runs on Kotlin/Native (no JVM-style reflection). Safe to call multiple times.
 */
fun ensureSyncForgeNetworkKtorLoaded() {
    KtorSyncTransport.defaultJson
}