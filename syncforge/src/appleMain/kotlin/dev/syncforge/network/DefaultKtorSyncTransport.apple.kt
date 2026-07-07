package dev.syncforge.network

internal actual fun createDefaultKtorSyncTransport(baseUrl: String, auth: SyncAuthProvider?): SyncTransport =
    throw IllegalStateException(
        "Default Ktor REST transport is not linked on this target. " +
            "Add studio.syncforge:syncforge-network-ktor and call " +
            "transport(KtorSyncTransport(baseUrl, auth)) in SyncForge.ios { } or SyncForge.macos { }.",
    )