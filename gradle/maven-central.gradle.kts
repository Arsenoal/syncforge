import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

val mavenCentralRepoBase = "https://repo1.maven.org/maven2/studio/syncforge"
val stagingApiBase = "https://ossrh-staging-api.central.sonatype.com"

/** Required Maven Central coordinates for a complete consumer-facing release. */
val mavenCentralRequiredArtifacts = listOf(
    "syncforge",
    "syncforge-android",
    "syncforge-jvm",
    "syncforge-annotations",
    "syncforge-persistence",
    "syncforge-android-deps",
    "syncforge-network-ktor",
    "syncforge-transport-core",
    "syncforge-transport-supabase",
    "syncforge-transport-firebase",
    "syncforge-store-room",
    "syncforge-store-inmemory",
    "syncforge-integration-koin",
    "syncforge-integration-hilt",
    "syncforge-bom",
    "syncforge-ksp",
    "syncforge-gradle-plugin",
)

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

class MavenCentralStagingClient(
    private val username: String,
    private val password: String,
) {
    private val authHeader: String =
        "Bearer ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"

    fun request(method: String, path: String): String {
        val connection = URI("$stagingApiBase$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", authHeader)
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
        if (code !in 200..299) {
            throw GradleException("Staging API $method $path failed: HTTP $code $body")
        }
        return body
    }

    fun requestOrNull(method: String, path: String): String? =
        try {
            request(method, path)
        } catch (_: GradleException) {
            null
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
        val client = MavenCentralStagingClient(username, password)
        val finalizeCurrentIp = System.getenv("FINALIZE_CURRENT_IP")?.toBooleanStrictOrNull() ?: true
        val strict = System.getenv("STRICT")?.toBooleanStrictOrNull() ?: false
        val dropInvalid = System.getenv("DROP_INVALID_ON_FAILURE")?.toBooleanStrictOrNull() ?: true
        val dropAllOpen = System.getenv("DROP_ALL_OPEN_STAGING")?.toBooleanStrictOrNull() ?: false

        if (finalizeCurrentIp) {
            logger.lifecycle("Finalizing staging upload for namespace $namespace (current CI IP)...")
            val body = client.requestOrNull("POST", "/manual/upload/defaultRepository/$namespace")
            if (body != null) {
                logger.lifecycle(body)
            } else {
                val message = "No current-IP staging upload to finalize (ok before a fresh publish)."
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