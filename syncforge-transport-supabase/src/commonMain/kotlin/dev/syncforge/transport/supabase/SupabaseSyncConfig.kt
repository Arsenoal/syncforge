package dev.syncforge.transport.supabase

/**
 * Supabase project connection for [SupabaseSyncDeltaStore].
 *
 * @param projectUrl Supabase project URL (e.g. `https://xyz.supabase.co`)
 * @param apiKey anon or service-role key (service role bypasses RLS)
 * @param accessToken optional user JWT for RLS-aware RPC calls; defaults to [apiKey]
 */
data class SupabaseSyncConfig(
    val projectUrl: String,
    val apiKey: String,
    val accessToken: (suspend () -> String?)? = null,
    val pushRpcName: String = DEFAULT_PUSH_RPC,
    val pullRpcName: String = DEFAULT_PULL_RPC,
) {
    val restBaseUrl: String = "${projectUrl.trimEnd('/')}/rest/v1"

    companion object {
        const val DEFAULT_PUSH_RPC: String = "syncforge_push"
        const val DEFAULT_PULL_RPC: String = "syncforge_pull"
    }
}