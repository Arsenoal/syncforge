package dev.syncforge.conflict

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
class CrdtEntityBuilder<T : SyncedEntity>(
    private val entitySerializer: KSerializer<T>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val fieldMergers = linkedMapOf<String, CrdtJsonFieldMerger>()
    private var onRemoteDeleteStrategy: ConflictStrategy = DeleteLocalOnRemoteTombstoneStrategy

    fun field(name: String, block: CrdtFieldBuilder.() -> Unit) {
        val builder = CrdtFieldBuilder().apply(block)
        fieldMergers[name] = builder.merger
            ?: error("field(\"$name\") requires a CRDT kind — e.g. orSet(), gCounter(), or lwwRegister()")
    }

    fun onRemoteDelete(block: ConflictEntityBuilder.() -> Unit) {
        onRemoteDeleteStrategy = ConflictEntityBuilder().strategyForRemoteDelete(block)
    }

    internal fun build(): ConflictStrategy =
        CrdtMergeStrategy(entitySerializer, fieldMergers, onRemoteDeleteStrategy, json)
}

/** Per-field CRDT kind inside [CrdtEntityBuilder.field]. */
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