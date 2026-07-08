package dev.syncforge.backendstarterspring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "syncforge")
data class SyncForgeProperties(
    val store: Store = Store(),
) {
    data class Store(
        val type: Type = Type.IN_MEMORY,
    )

    enum class Type {
        IN_MEMORY,
        JDBC,
    }
}