import dev.syncforge.webspike.JsPlatformSpike
import dev.syncforge.webspike.WebSpikeMarkers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Browser entry for the JS spike bundle — logs PoC markers on load.
 */
fun main() {
    console.log("SyncForge web spike ${WebSpikeMarkers.SPIKE_VERSION} (js)")
    val client = JsPlatformSpike.createHttpClient()
    console.log("Ktor JS client: ${client::class.simpleName}")
    MainScope().launch {
        val count = JsPlatformSpike.persistenceRoundTrip()
        console.log("SQLDelight sqljs outbox rows: $count")
    }
}