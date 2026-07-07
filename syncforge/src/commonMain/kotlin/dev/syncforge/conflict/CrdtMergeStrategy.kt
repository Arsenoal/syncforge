package dev.syncforge.conflict

import dev.syncforge.conflict.crdt.CrdtFieldMergeContext
import dev.syncforge.conflict.crdt.CrdtJsonFieldMerger
import dev.syncforge.conflict.crdt.LwwRegisterJsonFieldMerger
import dev.syncforge.entity.SyncedEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@PublishedApi
internal class CrdtMergeStrategy<T : SyncedEntity>(
    private val entitySerializer: KSerializer<T>,
    private val fieldMergers: Map<String, CrdtJsonFieldMerger>,
    private val onRemoteDelete: ConflictStrategy = DeleteLocalOnRemoteTombstoneStrategy,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ConflictStrategy {

    init {
        require(fieldMergers.isNotEmpty()) { "crdt { } requires at least one field(\"…\") { … }" }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : SyncedEntity> resolve(context: ConflictContext<T>): ConflictOutcome<T> {
        if (context.remote.isDeleted) {
            return onRemoteDelete.resolveRemoteDelete(context)
        }
        val remote = context.remotePayload
            ?: return ConflictOutcome.Resolved(ConflictResolution.KeepLocal(context.local))

        val mergeContext = CrdtFieldMergeContext(
            localUpdatedAtMillis = context.local.updatedAtMillis,
            remoteUpdatedAtMillis = context.remote.updatedAtMillis,
        )
        val serializer = entitySerializer as KSerializer<T>
        val localJson = json.encodeToJsonElement(serializer, context.local)
        val remoteJson = json.encodeToJsonElement(serializer, remote)
        val mergedJson = mergeJsonObjects(
            local = localJson,
            remote = remoteJson,
            mergeContext = mergeContext,
        )
        val merged = json.decodeFromJsonElement(serializer, mergedJson)
        return ConflictOutcome.Resolved(ConflictResolution.Merged(merged))
    }

    private fun mergeJsonObjects(
        local: JsonElement,
        remote: JsonElement,
        mergeContext: CrdtFieldMergeContext,
    ): JsonObject {
        val localObject = local as? JsonObject
            ?: error("crdt { } requires @Serializable entity JSON objects")
        val remoteObject = remote as? JsonObject
            ?: error("crdt { } requires @Serializable entity JSON objects")

        val fieldNames = localObject.keys + remoteObject.keys
        return buildJsonObject {
            for (fieldName in fieldNames) {
                val localValue = localObject[fieldName]
                val remoteValue = remoteObject[fieldName]
                val merger = fieldMergers[fieldName] ?: LwwRegisterJsonFieldMerger
                val mergedValue = merger.merge(localValue, remoteValue, mergeContext) ?: JsonNull
                put(fieldName, mergedValue)
            }
        }
    }
}