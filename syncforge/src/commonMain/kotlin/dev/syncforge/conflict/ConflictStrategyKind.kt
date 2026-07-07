package dev.syncforge.conflict

/**
 * Catalog of built-in conflict resolver kinds for per-entity policy configuration.
 *
 * Simple kinds resolve via [ConflictStrategies.fromKind]. [MERGE], [GIT_LIKE], and [CRDT]
 * require configured DSL blocks (`merge { }`, `gitLike { }`, `crdt { }`) — see [ConflictStrategies.fromKind].
 */
enum class ConflictStrategyKind {
    ACCEPT_LOCAL,
    ACCEPT_REMOTE,
    MERGE,
    GIT_LIKE,
    DEFER_TO_USER,
    LAST_WRITE_WINS,
    CRDT,
}