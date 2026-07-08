package dev.syncforge.transport.graphql

/**
 * GraphQL-over-HTTP endpoint for SyncForge push/pull operations.
 *
 * @param endpointUrl Full URL to the GraphQL endpoint (e.g. `https://api.example.com/graphql`).
 * @param bearerToken Optional suspend provider for `Authorization: Bearer` headers.
 */
data class GraphQlSyncConfig(
    val endpointUrl: String,
    val bearerToken: (suspend () -> String?)? = null,
)