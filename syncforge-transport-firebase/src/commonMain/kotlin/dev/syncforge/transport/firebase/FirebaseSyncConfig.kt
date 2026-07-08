package dev.syncforge.transport.firebase

/**
 * Firebase Cloud Functions (HTTPS) endpoints for [FirebaseSyncDeltaStore].
 *
 * Deploy `firebase/functions` to your project, then point [pushUrl] / [pullUrl] at the
 * `syncforgePush` and `syncforgePull` function URLs.
 *
 * @param idToken Firebase Auth ID token per request (recommended for production)
 */
data class FirebaseSyncConfig(
    val pushUrl: String,
    val pullUrl: String,
    val idToken: (suspend () -> String?)? = null,
) {
    companion object {
        /** Gen-1 Cloud Functions URL pattern (`https://{region}-{project}.cloudfunctions.net/{name}`). */
        fun cloudFunctions(
            projectId: String,
            region: String = "us-central1",
            pushFunctionName: String = DEFAULT_PUSH_FUNCTION,
            pullFunctionName: String = DEFAULT_PULL_FUNCTION,
            idToken: (suspend () -> String?)? = null,
        ): FirebaseSyncConfig {
            val base = "https://$region-$projectId.cloudfunctions.net"
            return FirebaseSyncConfig(
                pushUrl = "$base/$pushFunctionName",
                pullUrl = "$base/$pullFunctionName",
                idToken = idToken,
            )
        }

        const val DEFAULT_PUSH_FUNCTION: String = "syncforgePush"
        const val DEFAULT_PULL_FUNCTION: String = "syncforgePull"
    }
}