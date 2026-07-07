package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Persists the last successfully synced entity JSON per `(entityType, entityId)`.
 *
 * Written on push acknowledgement and non-conflict pull apply; removed on entity delete.
 * Powers git-like three-way merge in 1.2+.
 */
@ExperimentalSyncForgeApi
interface MergeBaseStore {

    suspend fun get(entityType: String, entityId: String): MergeBaseSnapshot?

    suspend fun put(snapshot: MergeBaseSnapshot)

    suspend fun remove(entityType: String, entityId: String)
}

@ExperimentalSyncForgeApi
object NoOpMergeBaseStore : MergeBaseStore {

    override suspend fun get(entityType: String, entityId: String): MergeBaseSnapshot? = null

    override suspend fun put(snapshot: MergeBaseSnapshot) = Unit

    override suspend fun remove(entityType: String, entityId: String) = Unit
}