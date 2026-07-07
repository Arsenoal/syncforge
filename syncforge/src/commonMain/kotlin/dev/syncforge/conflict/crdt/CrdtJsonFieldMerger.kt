package dev.syncforge.conflict.crdt

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class CrdtFieldMergeContext(
    val localUpdatedAtMillis: Long,
    val remoteUpdatedAtMillis: Long,
)

internal fun interface CrdtJsonFieldMerger {
    fun merge(
        local: JsonElement?,
        remote: JsonElement?,
        context: CrdtFieldMergeContext,
    ): JsonElement?
}

private val crdtJson = Json { ignoreUnknownKeys = true }

internal object OrSetJsonFieldMerger : CrdtJsonFieldMerger {
    override fun merge(
        local: JsonElement?,
        remote: JsonElement?,
        context: CrdtFieldMergeContext,
    ): JsonElement? {
        if (local is JsonObject && remote is JsonObject && looksLikeOrSet(local) && looksLikeOrSet(remote)) {
            val merged = crdtJson.decodeFromJsonElement(OrSet.serializer(String.serializer()), local)
                .merge(crdtJson.decodeFromJsonElement(OrSet.serializer(String.serializer()), remote))
            return crdtJson.encodeToJsonElement(OrSet.serializer(String.serializer()), merged)
        }
        if (local is JsonArray || remote is JsonArray) {
            return unionJsonArrays(local?.jsonArrayOrNull(), remote?.jsonArrayOrNull())
        }
        return remote ?: local
    }

    private fun looksLikeOrSet(element: JsonObject): Boolean =
        element.containsKey("adds") && element.containsKey("removes")

    private fun unionJsonArrays(local: JsonArray?, remote: JsonArray?): JsonArray {
        val seen = linkedSetOf<String>()
        return buildJsonArray {
            for (element in local.orEmpty() + remote.orEmpty()) {
                val key = element.stableKey()
                if (seen.add(key)) add(element)
            }
        }
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList() ?: emptyList()
}

internal object GCounterJsonFieldMerger : CrdtJsonFieldMerger {
    override fun merge(
        local: JsonElement?,
        remote: JsonElement?,
        context: CrdtFieldMergeContext,
    ): JsonElement? {
        if (local is JsonObject && remote is JsonObject && looksLikeGCounter(local) && looksLikeGCounter(remote)) {
            val merged = crdtJson.decodeFromJsonElement(GCounter.serializer(), local)
                .merge(crdtJson.decodeFromJsonElement(GCounter.serializer(), remote))
            return crdtJson.encodeToJsonElement(GCounter.serializer(), merged)
        }
        val localInt = local?.jsonPrimitive?.intOrNull
        val remoteInt = remote?.jsonPrimitive?.intOrNull
        return when {
            localInt != null && remoteInt != null -> JsonPrimitive(maxOf(localInt, remoteInt))
            remoteInt != null -> JsonPrimitive(remoteInt)
            localInt != null -> JsonPrimitive(localInt)
            else -> remote ?: local
        }
    }

    private fun looksLikeGCounter(element: JsonObject): Boolean = element.containsKey("counts")
}

internal object LwwRegisterJsonFieldMerger : CrdtJsonFieldMerger {
    override fun merge(
        local: JsonElement?,
        remote: JsonElement?,
        context: CrdtFieldMergeContext,
    ): JsonElement? {
        if (local is JsonObject && remote is JsonObject && looksLikeLwwRegister(local) && looksLikeLwwRegister(remote)) {
            val serializer = LwwRegister.serializer(String.serializer())
            val merged = crdtJson.decodeFromJsonElement(serializer, local)
                .merge(crdtJson.decodeFromJsonElement(serializer, remote))
            return crdtJson.encodeToJsonElement(serializer, merged)
        }
        return if (context.remoteUpdatedAtMillis >= context.localUpdatedAtMillis) {
            remote ?: local
        } else {
            local ?: remote
        }
    }

    private fun looksLikeLwwRegister(element: JsonObject): Boolean =
        element.containsKey("value") && element.containsKey("timestamp")
}

private fun JsonElement?.jsonArrayOrNull(): JsonArray? =
    when (this) {
        is JsonArray -> this
        else -> null
    }

private fun JsonElement.stableKey(): String =
    when (this) {
        is JsonPrimitive -> content
        else -> toString()
    }