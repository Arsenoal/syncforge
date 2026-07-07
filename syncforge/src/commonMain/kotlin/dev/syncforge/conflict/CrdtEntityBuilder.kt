package dev.syncforge.conflict

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.crdt.CrdtJsonFieldMerger
import dev.syncforge.conflict.crdt.GCounterJsonFieldMerger
import dev.syncforge.conflict.crdt.LwwRegisterJsonFieldMerger
import dev.syncforge.conflict.crdt.OrSetJsonFieldMerger
import dev.syncforge.entity.SyncedEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * DSL builder for [ConflictEntityBuilder.crdt].
 */
@ExperimentalSyncForgeApi
class CrdtEntityBuilder<T : SyncedEntity>(
    private val entitySerializer: KSerializer<T>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val fieldMergers = linkedMapOf<String, CrdtJsonFieldMerger>()

    fun field(name: String, block: CrdtFieldBuilder.() -> Unit) {
        val builder = CrdtFieldBuilder().apply(block)
        fieldMergers[name] = builder.merger
            ?: error("field(\"$name\") requires a CRDT kind — e.g. orSet(), gCounter(), or lwwRegister()")
    }

    internal fun build(): ConflictStrategy =
        CrdtMergeStrategy(entitySerializer, fieldMergers, json)
}

/** Per-field CRDT kind inside [CrdtEntityBuilder.field]. */
@ExperimentalSyncForgeApi
class CrdtFieldBuilder {
    internal var merger: CrdtJsonFieldMerger? = null

    fun orSet() {
        merger = OrSetJsonFieldMerger
    }

    fun gCounter() {
        merger = GCounterJsonFieldMerger
    }

    fun lwwRegister() {
        merger = LwwRegisterJsonFieldMerger
    }
}