package dev.syncforge.network

internal actual fun createDefaultKtorSyncTransport(baseUrl: String, auth: SyncAuthProvider?): SyncTransport =
    KtorSyncTransportReflection.create(baseUrl, auth)