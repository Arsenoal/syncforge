package dev.syncforge.backendstarterspring

import dev.syncforge.server.InMemorySyncStore
import dev.syncforge.server.JdbcSyncStore
import dev.syncforge.server.SyncStore
import dev.syncforge.server.auth.InMemoryAuthStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
@EnableConfigurationProperties(SyncForgeProperties::class)
class SyncForgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun inMemoryAuthStore(): InMemoryAuthStore = InMemoryAuthStore()

    @Bean
    @ConditionalOnProperty(prefix = "syncforge.store", name = ["type"], havingValue = "in-memory", matchIfMissing = true)
    @ConditionalOnMissingBean
    fun inMemorySyncStore(): SyncStore = InMemorySyncStore()

    @Bean
    @ConditionalOnProperty(prefix = "syncforge.store", name = ["type"], havingValue = "jdbc")
    @ConditionalOnMissingBean
    fun jdbcSyncStore(dataSource: DataSource): SyncStore = JdbcSyncStore(dataSource)
}