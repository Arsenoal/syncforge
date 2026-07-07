package dev.syncforge.network

import io.ktor.client.HttpClient

internal object KtorSyncTransportReflection {
    fun create(baseUrl: String, auth: SyncAuthProvider?, httpClient: HttpClient? = null): SyncTransport {
        SyncForgeNetworkKtorRuntime.createKtorSyncTransport(baseUrl, auth, httpClient)?.let { return it }

        return try {
            Class.forName("dev.syncforge.network.KtorSyncTransport")
            SyncForgeNetworkKtorRuntime.createKtorSyncTransport(baseUrl, auth, httpClient)?.let { return it }

            val clazz = Class.forName("dev.syncforge.network.KtorSyncTransport")
            val ctor = if (httpClient == null) {
                clazz.getConstructor(String::class.java, SyncAuthProvider::class.java)
                    .newInstance(baseUrl, auth)
            } else {
                clazz.getConstructor(String::class.java, SyncAuthProvider::class.java, HttpClient::class.java)
                    .newInstance(baseUrl, auth, httpClient)
            }
            ctor as SyncTransport
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException(
                "Default REST transport requires studio.syncforge:syncforge-network-ktor on the classpath. " +
                    "Add the dependency or call transport { } with a custom SyncTransport.",
                e,
            )
        }
    }
}