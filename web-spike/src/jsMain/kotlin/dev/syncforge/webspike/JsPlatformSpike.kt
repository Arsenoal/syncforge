package dev.syncforge.webspike

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import dev.syncforge.webspike.persistence.WebSpikeDatabase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.w3c.dom.Worker

/**
 * Kotlin/JS PoC: Ktor browser client + SQLDelight web-worker driver (1.6-00).
 */
object JsPlatformSpike {

    fun createHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
    }

    suspend fun createDatabase(): WebSpikeDatabase {
        val driver = WebWorkerDriver(
            Worker(
                js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)"""),
            ),
        )
        WebSpikeDatabase.Schema.awaitCreate(driver)
        return WebSpikeDatabase(driver)
    }

    suspend fun persistenceRoundTrip(): Long {
        val database = createDatabase()
        database.spikeOutboxQueries.insertEntry(entity_id = "spike-js", created_at_millis = 1L)
        return database.spikeOutboxQueries.countEntries().awaitAsOne()
    }
}