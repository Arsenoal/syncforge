package dev.syncforge.model

import kotlinx.serialization.Serializable

/**
 * The kind of mutation captured in the outbox and sent to the backend.
 */
@Serializable
enum class ChangeType {
    CREATE,
    UPDATE,
    DELETE,
}