package dev.syncforge.transport.contract

import dev.syncforge.transport.SyncDeltaStore

/** Clock + store pair for [SyncDeltaStoreContract] scenarios. */
data class ContractTestContext(
    val store: SyncDeltaStore,
    val setClock: (Long) -> Unit,
)