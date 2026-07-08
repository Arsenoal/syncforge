package dev.syncforge.transport.supabase

/**
 * Supabase Realtime integration notes for [SupabaseSyncDeltaStore].
 *
 * After applying the SQL migration, `sync_entity` is added to the `supabase_realtime`
 * publication. Subscribe to `postgres_changes` and trigger a sync when remote rows change:
 *
 * ```
 * // Pseudocode — use supabase-kt or your Realtime client:
 * channel.onPostgresChange(PostgresAction.All, schema = "public", table = "sync_entity") {
 *     syncManager.sync()
 * }
 * ```
 *
 * Realtime complements pull-by-cursor; it does not replace push or periodic full sync.
 */
object SupabaseRealtimePatterns {
    const val SYNC_TABLE: String = "sync_entity"
    const val REALTIME_PUBLICATION: String = "supabase_realtime"
}