import dev.syncforge.webspike.WasmPlatformSpike

/**
 * Browser entry for the Wasm transport-only spike (1.6-00).
 */
fun main() {
    WasmPlatformSpike.createHttpClient().close()
}