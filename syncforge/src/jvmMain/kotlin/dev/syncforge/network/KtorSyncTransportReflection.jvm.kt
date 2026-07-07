package dev.syncforge.network

internal object KtorSyncTransportReflection {
    fun create(baseUrl: String, auth: SyncAuthProvider?): SyncTransport =
        try {
            val clazz = Class.forName("dev.syncforge.network.KtorSyncTransport")
            val ctor = clazz.getConstructor(String::class.java, SyncAuthProvider::class.java)
            ctor.newInstance(baseUrl, auth) as SyncTransport
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException(
                "Default REST transport requires studio.syncforge:syncforge-network-ktor on the classpath. " +
                    "Add the dependency or call transport { } with a custom SyncTransport.",
                e,
            )
        }
}