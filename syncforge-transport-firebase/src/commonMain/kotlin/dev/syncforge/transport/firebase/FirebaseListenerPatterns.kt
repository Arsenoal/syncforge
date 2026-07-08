package dev.syncforge.transport.firebase

/**
 * Firestore listener integration notes for [FirebaseSyncDeltaStore].
 *
 * After deploying Cloud Functions, attach a snapshot listener on [FirebaseFirestoreSchema.SYNC_ENTITY_COLLECTION]
 * and call `syncManager.sync()` when documents change:
 *
 * ```
 * // Pseudocode — use Firebase SDK on Android/iOS:
 * firestore.collection("sync_entity")
 *     .whereGreaterThan("updatedAtMillis", lastKnownCursor)
 *     .addSnapshotListener { _, _ -> syncManager.sync() }
 * ```
 *
 * Listeners complement pull-by-cursor; they do not replace push or periodic full sync.
 */
object FirebaseListenerPatterns {
    const val UPDATED_AT_FIELD: String = "updatedAtMillis"
}