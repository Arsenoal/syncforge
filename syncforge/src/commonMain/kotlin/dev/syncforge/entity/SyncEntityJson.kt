package dev.syncforge.entity

/**
 * Patches serialized [SyncedEntity] JSON without requiring a typed `copy(localVersion = …)`.
 */
internal object SyncEntityJson {

    private val LOCAL_VERSION_REGEX = """"localVersion"\s*:\s*\d+""".toRegex()

    fun withLocalVersion(json: String, localVersion: Long): String =
        when {
            LOCAL_VERSION_REGEX.containsMatchIn(json) ->
                LOCAL_VERSION_REGEX.replaceFirst(json, """"localVersion":$localVersion""")
            json.startsWith("{") ->
                json.replaceFirst("{", """{"localVersion":$localVersion,""")
            else -> json
        }
}