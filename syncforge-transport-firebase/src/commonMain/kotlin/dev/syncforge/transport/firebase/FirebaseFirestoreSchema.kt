package dev.syncforge.transport.firebase

/**
 * Firestore layout used by the reference Cloud Functions in `firebase/functions/`.
 */
object FirebaseFirestoreSchema {
    const val SYNC_ENTITY_COLLECTION: String = "sync_entity"
    const val METADATA_COLLECTION: String = "metadata"
    const val VERSION_COUNTER_DOCUMENT: String = "version_counter"
    const val VERSION_COUNTER_FIELD: String = "nextVersion"

    fun documentId(entityType: String, entityId: String): String = "$entityType:$entityId"
}