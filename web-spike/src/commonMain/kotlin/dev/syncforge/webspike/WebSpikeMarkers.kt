package dev.syncforge.webspike

/**
 * Markers referenced by [docs/WEB_SPIKE.md] — compile-time proof that shared KMP code
 * links on browser targets (1.6-00 spike; not shipped in BOM).
 */
object WebSpikeMarkers {
    const val SPIKE_VERSION = "1.6-00"
    const val REST_PUSH_PATH = "/sync/push"
    const val REST_PULL_PATH = "/sync/pull"
}