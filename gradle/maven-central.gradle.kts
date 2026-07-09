import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

val mavenCentralRepoBase = "https://repo1.maven.org/maven2/studio/syncforge"
val stagingApiBase = "https://ossrh-staging-api.central.sonatype.com"

/**
 * Root artifact IDs for every library module published via [publishAllToMavenCentral].
 * Keep aligned with [publishableModules] in publish-convention.gradle.kts.
 */
val publishableLibraryArtifactIds = listOf(
    "syncforge",
    "syncforge-annotations",
    "syncforge-ksp",
    "syncforge-persistence",
    "syncforge-network-ktor",
    "syncforge-transport-core",
    "syncforge-transport-supabase",
    "syncforge-transport-firebase",
    "syncforge-transport-graphql",
    "syncforge-store-room",
    "syncforge-store-inmemory",
    "syncforge-integration-koin",
    "syncforge-integration-hilt",
    "syncforge-integration-opentelemetry",
    "syncforge-android-deps",
    "syncforge-catalog",
)

/** Extra platform POMs for the core KMP module (metadata + Android + JVM). */
val mavenCentralPlatformArtifactIds = listOf(
    "syncforge-android",
    "syncforge-jvm",
)

/** Gradle plugin marker published from the included :syncforge-gradle-plugin build. */
val mavenCentralGradlePluginArtifactIds = listOf(
    "syncforge-gradle-plugin",
)

/**
 * Required Maven Central coordinates checked by verifyMavenCentralArtifacts.
 * One root POM per publishable library + core platform variants + Gradle plugin.
 *
 * Intentionally excludes browser `js` and native Apple targets — `js` is monorepo-only;
 * iOS/macOS ship via SPM / XCFramework (publishIosSpmArtifacts).
 */
val mavenCentralRequiredArtifacts =
    publishableLibraryArtifactIds +
        mavenCentralPlatformArtifactIds +
        mavenCentralGradlePluginArtifactIds

fun Project.readGradlePropertiesVersion(propertiesFile: java.io.File): String {
    val props = java.util.Properties()
    propertiesFile.inputStream().use { props.load(it) }
    return props.getProperty("syncforge.version")
        ?: error("syncforge.version missing in ${propertiesFile.path}")
}

fun pomUrl(artifact: String, version: String): String =
    "$mavenCentralRepoBase/$artifact/$version/$artifact-$version.pom"

fun pomHttpStatus(url: String): Int =
    try {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.responseCode
    } catch (_: Exception) {
        -1
    }

fun verifyMavenCentralArtifacts(
    logger: org.gradle.api.logging.Logger,
    artifacts: List<String>,
    version: String,
    retries: Int,
    sleepSec: Int,
) {
    var missing = artifacts
    repeat(retries) { attempt ->
        missing = artifacts.filter { artifact ->
            val status = pomHttpStatus(pomUrl(artifact, version))
            if (status == 200) {
                logger.lifecycle("OK  $artifact:$version")
                false
            } else {
                logger.lifecycle("MISS $artifact:$version (HTTP $status)")
                true
            }
        }
        if (missing.isEmpty()) {
            logger.lifecycle("All ${artifacts.size} required artifacts are on Maven Central.")
            return
        }
        if (attempt < retries - 1) {
            logger.lifecycle(
                "Waiting ${sleepSec}s for Central sync (attempt ${attempt + 1}/$retries)...",
            )
            Thread.sleep(sleepSec * 1000L)
        }
    }
    throw GradleException("Missing on Maven Central: ${missing.joinToString()}")
}

fun mavenCentralStagingConnectTimeoutMs(): Int =
    System.getenv("MAVEN_CENTRAL_STAGING_CONNECT_TIMEOUT_MS")?.toIntOrNull() ?: 60_000

fun mavenCentralStagingReadTimeoutMs(): Int =
    System.getenv("MAVEN_CENTRAL_STAGING_READ_TIMEOUT_MS")?.toIntOrNull() ?: 300_000

fun mavenCentralStagingMaxRetries(): Int =
    System.getenv("MAVEN_CENTRAL_STAGING_MAX_RETRIES")?.toIntOrNull() ?: 3

fun mavenCentralStagingRetrySleepMs(attempt: Int): Long =
    (System.getenv("MAVEN_CENTRAL_STAGING_RETRY_SLEEP_SEC")?.toIntOrNull() ?: 15) *
        (attempt + 1) * 1000L

fun isRetryableStagingError(error: Throwable): Boolean =
    error is java.net.SocketTimeoutException ||
        error is java.net.ConnectException ||
        error is java.io.IOException

class MavenCentralStagingClient(
    private val username: String,
    private val password: String,
    private val logger: org.gradle.api.logging.Logger,
    private val connectTimeoutMs: Int = mavenCentralStagingConnectTimeoutMs(),
    private val readTimeoutMs: Int = mavenCentralStagingReadTimeoutMs(),
    private val maxRetries: Int = mavenCentralStagingMaxRetries(),
) {
    private val authHeader: String =
        "Bearer ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"

    private fun executeOnce(method: String, path: String): String {
        val connection = URI("$stagingApiBase$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", authHeader)
        connection.instanceFollowRedirects = true
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
        if (code !in 200..299) {
            throw GradleException("Staging API $method $path failed: HTTP $code $body")
        }
        return body
    }

    fun request(method: String, path: String): String {
        var lastError: Throwable? = null
        for (attempt in 0 until maxRetries) {
            try {
                return executeOnce(method, path)
            } catch (error: Throwable) {
                lastError = error
                if (attempt < maxRetries - 1 && isRetryableStagingError(error)) {
                    val sleepMs = mavenCentralStagingRetrySleepMs(attempt)
                    logger.lifecycle(
                        "Staging API $method $path failed (${error.javaClass.simpleName}: ${error.message}) " +
                            "— retry ${attempt + 2}/$maxRetries in ${sleepMs / 1000}s " +
                            "(readTimeout=${readTimeoutMs}ms)",
                    )
                    Thread.sleep(sleepMs)
                } else {
                    break
                }
            }
        }
        throw GradleException(
            "Staging API $method $path failed after $maxRetries attempt(s)",
            lastError,
        )
    }

    /** Returns null only when the server reports no staging upload (HTTP 404). */
    fun requestOrEmpty(method: String, path: String): String? {
        return try {
            request(method, path)
        } catch (error: GradleException) {
            if (error.message?.contains("HTTP 404") == true) null else throw error
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun promoteOrphanedStagingUploads(
    logger: org.gradle.api.logging.Logger,
    client: MavenCentralStagingClient,
    namespace: String,
    dropInvalidOnFailure: Boolean,
    dropAllOpenStaging: Boolean,
) {
    logger.lifecycle("Searching for orphaned staging repositories (any IP)...")
    val raw = client.request("GET", "/manual/search/repositories?ip=any&profile_id=$namespace")
    val data = JsonSlurper().parseText(raw) as Map<String, Any?>
    val repos = data["repositories"] as? List<Map<String, Any?>> ?: emptyList()
    if (repos.isEmpty()) {
        logger.lifecycle("No staging repositories found.")
        return
    }

    var promoted = 0
    var dropped = 0
    var skipped = 0
    var failed = 0

    for (repo in repos) {
        val key = repo["key"]?.toString() ?: continue
        val state = repo["state"]?.toString()
        val portalId = repo["portal_deployment_id"]
        logger.lifecycle("  repo=$key state=$state portal_deployment_id=$portalId")

        if (dropAllOpenStaging && portalId == null && state == "open") {
            runCatching {
                val body = client.request("DELETE", "/manual/drop/repository/$key")
                logger.lifecycle("  -> dropped open staging repo $key: $body")
                dropped++
            }.onFailure {
                logger.error("  -> failed to drop open repo $key: ${it.message}")
                failed++
            }
            continue
        }

        if (portalId != null) {
            skipped++
            continue
        }

        runCatching {
            val body = client.request("POST", "/manual/upload/repository/$key")
            logger.lifecycle("  -> promoted $key: $body")
            promoted++
        }.onFailure { error ->
            logger.error("  -> failed to promote $key: ${error.message}")
            if (dropInvalidOnFailure && error.message?.contains("HTTP 400") == true ||
                error.message?.contains("HTTP 422") == true
            ) {
                runCatching {
                    val dropBody = client.request("DELETE", "/manual/drop/repository/$key")
                    logger.lifecycle("  -> dropped invalid staging repo $key: $dropBody")
                    dropped++
                    return@onFailure
                }.onFailure { dropError ->
                    logger.error("  -> failed to drop $key: ${dropError.message}")
                }
            }
            failed++
        }
    }

    logger.lifecycle("Summary: promoted=$promoted dropped=$dropped skipped=$skipped failed=$failed")
    if (failed > 0) {
        throw GradleException("Maven Central staging finalize had $failed failure(s)")
    }
}

fun Project.mavenCentralVerifyRetries(): Int =
    providers.environmentVariable("MAVEN_CENTRAL_VERIFY_RETRIES").orNull?.toIntOrNull() ?: 12

fun Project.mavenCentralVerifySleepSec(): Int =
    providers.environmentVariable("MAVEN_CENTRAL_VERIFY_SLEEP_SEC").orNull?.toIntOrNull() ?: 30

fun Project.resolveVerifyMavenCentralVersion(): String =
    providers.gradleProperty("verifyMavenCentralVersion").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: readGradlePropertiesVersion(rootProject.file("gradle.properties"))

tasks.register("verifyMavenCentralArtifactList") {
    group = "verification"
    description =
        "Fails unless mavenCentralRequiredArtifacts covers every publishable library, " +
            "core platform variants, and the Gradle plugin."
    doLast {
        val publishableModules = setOf(
            "syncforge",
            "syncforge-annotations",
            "syncforge-ksp",
            "syncforge-persistence",
            "syncforge-network-ktor",
            "syncforge-transport-core",
            "syncforge-transport-supabase",
            "syncforge-transport-firebase",
            "syncforge-transport-graphql",
            "syncforge-store-room",
            "syncforge-store-inmemory",
            "syncforge-integration-koin",
            "syncforge-integration-hilt",
            "syncforge-integration-opentelemetry",
            "syncforge-android-deps",
            "syncforge-catalog",
        )
        val required = mavenCentralRequiredArtifacts.toSet()
        val missingLibraries = publishableModules - required
        check(missingLibraries.isEmpty()) {
            "mavenCentralRequiredArtifacts missing publishable libraries: ${missingLibraries.joinToString()}"
        }
        val missingPlatforms = mavenCentralPlatformArtifactIds.toSet() - required
        check(missingPlatforms.isEmpty()) {
            "mavenCentralRequiredArtifacts missing platform POMs: ${missingPlatforms.joinToString()}"
        }
        val missingPlugin = mavenCentralGradlePluginArtifactIds.toSet() - required
        check(missingPlugin.isEmpty()) {
            "mavenCentralRequiredArtifacts missing Gradle plugin: ${missingPlugin.joinToString()}"
        }
        val unexpected = required - publishableModules - mavenCentralPlatformArtifactIds.toSet() -
            mavenCentralGradlePluginArtifactIds.toSet()
        check(unexpected.isEmpty()) {
            "mavenCentralRequiredArtifacts has unexpected entries: ${unexpected.joinToString()}"
        }
        logger.lifecycle(
            "mavenCentralRequiredArtifacts covers ${publishableModules.size} libraries + " +
                "${mavenCentralPlatformArtifactIds.size} platform POMs + Gradle plugin " +
                "(${required.size} total).",
        )
    }
}

tasks.register("verifyMavenCentralArtifacts") {
    group = "verification"
    description = "Fails unless required SyncForge POMs resolve on repo1.maven.org."
    doLast {
        verifyMavenCentralArtifacts(
            logger = logger,
            artifacts = mavenCentralRequiredArtifacts,
            version = resolveVerifyMavenCentralVersion(),
            retries = mavenCentralVerifyRetries(),
            sleepSec = mavenCentralVerifySleepSec(),
        )
    }
}

tasks.register("verifyConsumerSmokeMavenCentralArtifacts") {
    group = "verification"
    description =
        "Fails unless required SyncForge POMs resolve on repo1.maven.org for the consumer-smoke Maven Central pin."
    doLast {
        verifyMavenCentralArtifacts(
            logger = logger,
            artifacts = mavenCentralRequiredArtifacts,
            version = readGradlePropertiesVersion(rootProject.file("consumer-smoke/android-minimal/gradle.properties")),
            retries = mavenCentralVerifyRetries(),
            sleepSec = mavenCentralVerifySleepSec(),
        )
    }
}

tasks.register("finalizeMavenCentralStaging") {
    group = "publishing"
    description =
        "Transfers OSSRH staging uploads to Central Portal (replaces legacy shell finalize script)."
    doLast {
        val namespace = providers.gradleProperty("mavenCentralNamespace").orNull ?: "studio.syncforge"
        val username = System.getenv("MAVEN_CENTRAL_USERNAME")
            ?: error("MAVEN_CENTRAL_USERNAME is required")
        val password = System.getenv("MAVEN_CENTRAL_PASSWORD")
            ?: error("MAVEN_CENTRAL_PASSWORD is required")
        val client = MavenCentralStagingClient(username, password, logger)
        val finalizeCurrentIp = System.getenv("FINALIZE_CURRENT_IP")?.toBooleanStrictOrNull() ?: true
        val strict = System.getenv("STRICT")?.toBooleanStrictOrNull() ?: false
        val dropInvalid = System.getenv("DROP_INVALID_ON_FAILURE")?.toBooleanStrictOrNull() ?: true
        val dropAllOpen = System.getenv("DROP_ALL_OPEN_STAGING")?.toBooleanStrictOrNull() ?: false

        if (finalizeCurrentIp) {
            logger.lifecycle(
                "Finalizing staging upload for namespace $namespace (current CI IP) " +
                    "(connectTimeout=${mavenCentralStagingConnectTimeoutMs()}ms, " +
                    "readTimeout=${mavenCentralStagingReadTimeoutMs()}ms, " +
                    "retries=${mavenCentralStagingMaxRetries()})...",
            )
            val body = client.requestOrEmpty("POST", "/manual/upload/defaultRepository/$namespace")
            if (body != null) {
                logger.lifecycle(body)
            } else {
                val message = "No current-IP staging upload to finalize (HTTP 404 — ok if publish already transferred)."
                if (strict) error(message) else logger.lifecycle(message)
            }
        }

        runCatching {
            promoteOrphanedStagingUploads(
                logger = logger,
                client = client,
                namespace = namespace,
                dropInvalidOnFailure = dropInvalid,
                dropAllOpenStaging = dropAllOpen,
            )
        }.onFailure { error ->
            if (strict) throw error else logger.warn(error.message ?: "Staging orphan promotion skipped")
        }
    }
}