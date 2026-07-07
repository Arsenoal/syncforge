package dev.syncforge

import androidx.test.core.app.ApplicationProvider
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.model.SyncState
import dev.syncforge.network.AlwaysOnlineNetworkMonitor
import dev.syncforge.network.KtorSyncTransport
import dev.syncforge.network.SyncHttpRoutes
import dev.syncforge.test.FakeEntitySyncHandler
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalSyncForgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidHttpClientDslTest {

    @Test
    fun androidDsl_httpClient_usesInjectedClientForDefaultTransport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val handler = FakeEntitySyncHandler("tasks")
        var pushHit = false

        val appClient = HttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    SyncHttpRoutes.PUSH_PATH -> {
                        pushHit = true
                        respond(
                            content = """{"acknowledgedIds":[],"rejected":[]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            },
        ) {
            install(ContentNegotiation) {
                json(KtorSyncTransport.defaultJson)
            }
        }

        val manager = SyncForge.android(context) {
            baseUrl("https://api.example.com")
            httpClient(appClient)
            registry(EntityRegistry.of(handler))
            customize {
                networkMonitor = AlwaysOnlineNetworkMonitor
                schedulePeriodicSyncOnStart = false
            }
        }

        kotlinx.coroutines.test.runTest {
            manager.enqueueChange(
                Change.create(
                    entityType = "tasks",
                    entity = object : SyncedEntity {
                        override val id: String = "t1"
                        override val localVersion: Long = 1
                        override val updatedAtMillis: Long = 1
                        override val syncState: SyncState = SyncState.PENDING
                    },
                ),
            )
            manager.push()
        }
        assertTrue(pushHit)
    }
}