plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDependencyManagement) apply false
}

apply(from = "gradle/publish-convention.gradle.kts")
apply(from = "gradle/maven-central.gradle.kts")
apply(from = "gradle/e2e.gradle.kts")
apply(from = "gradle/ios-xcode.gradle.kts")
apply(from = "gradle/ios-distribution.gradle.kts")

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes all SyncForge library modules to the local Maven repository."
    dependsOn(
        ":syncforge:publishToMavenLocal",
        ":syncforge-annotations:publishToMavenLocal",
        ":syncforge-ksp:publishToMavenLocal",
        ":syncforge-persistence:publishToMavenLocal",
        ":syncforge-network-ktor:publishToMavenLocal",
        ":syncforge-transport-core:publishToMavenLocal",
        ":syncforge-transport-supabase:publishToMavenLocal",
        ":syncforge-transport-firebase:publishToMavenLocal",
        ":syncforge-transport-graphql:publishToMavenLocal",
        ":syncforge-store-room:publishToMavenLocal",
        ":syncforge-store-inmemory:publishToMavenLocal",
        ":syncforge-integration-koin:publishToMavenLocal",
        ":syncforge-integration-hilt:publishToMavenLocal",
        ":syncforge-integration-opentelemetry:publishToMavenLocal",
        ":syncforge-android-deps:publishToMavenLocal",
        ":syncforge-bom:publishToMavenLocal",
        ":syncforge-catalog:publishToMavenLocal",
        gradle.includedBuild("syncforge-gradle-plugin").task(":publishToMavenLocal"),
    )
}

tasks.register("verifyWebSpike") {
    group = "verification"
    description = "1.6-00 web spike compile check (js + wasmJs); see docs/WEB_SPIKE.md."
    dependsOn(
        ":web-spike:compileKotlinJs",
        ":web-spike-wasm:compileKotlinWasmJs",
    )
}

tasks.register("verifyWebCompile") {
    group = "verification"
    description = "1.6-01 web target compile check (:syncforge + persistence + network-ktor js)."
    dependsOn(
        ":syncforge-annotations:compileKotlinJs",
        ":syncforge-persistence:compileKotlinJs",
        ":syncforge:compileKotlinJs",
        ":syncforge-network-ktor:compileKotlinJs",
    )
}

tasks.register("verifyReleaseSignOff") {
    group = "verification"
    description =
        "Pre-publish sign-off: JVM + Android unit tests, server tests, consumer smoke, and compile checks."
    dependsOn(
        ":syncforge:jvmTest",
        ":syncforge:testDebugUnitTest",
        ":syncforge-network-ktor:jvmTest",
        ":syncforge-network-ktor:testDebugUnitTest",
        ":syncforge-transport-core:jvmTest",
        ":syncforge-transport-core:testDebugUnitTest",
        ":syncforge-transport-supabase:jvmTest",
        ":syncforge-transport-supabase:testDebugUnitTest",
        ":syncforge-transport-firebase:jvmTest",
        ":syncforge-transport-firebase:testDebugUnitTest",
        ":syncforge-transport-graphql:jvmTest",
        ":syncforge-transport-graphql:testDebugUnitTest",
        ":syncforge-store-room:testDebugUnitTest",
        ":syncforge-store-inmemory:jvmTest",
        ":syncforge-store-inmemory:testDebugUnitTest",
        ":syncforge-integration-koin:compileReleaseKotlin",
        ":syncforge-integration-hilt:compileReleaseKotlin",
        ":syncforge-integration-opentelemetry:compileKotlinJvm",
        ":syncforge-integration-opentelemetry:compileReleaseKotlinAndroid",
        ":syncforge-server:test",
        ":syncforge-persistence:compileDebugKotlinAndroid",
        ":syncforge-persistence:compileKotlinJvm",
        ":syncforge-annotations:compileDebugKotlinAndroid",
        ":syncforge-annotations:compileKotlinJvm",
        ":syncforge-ksp:compileKotlin",
        ":backend-starter:compileKotlin",
        ":backend-starter-graphql:compileKotlin",
        ":backend-starter-spring:test",
        ":mock-server:compileKotlin",
        ":sample-desktop:compileKotlin",
        ":sample-web:compileKotlinJs",
        ":sample:compileDebugKotlin",
        ":sample:compileDebugAndroidTestKotlin",
        ":syncforge-bom:verifyBomConstraints",
        ":syncforge-catalog:verifyCatalogArtifacts",
        "verifyConsumerSmoke",
        "verifyWebSpike",
        "verifyWebCompile",
    )
}

fun readSyncforgeVersion(propertiesFile: java.io.File): String {
    val props = java.util.Properties()
    propertiesFile.inputStream().use { props.load(it) }
    return props.getProperty("syncforge.version")
        ?: error("syncforge.version missing in ${propertiesFile.path}")
}

fun syncforgeLibraryVersion(): String = readSyncforgeVersion(rootProject.file("gradle.properties"))

/** Maven Central publish is gated to 2.0.0+ unless [allowPre2MavenCentralPublish] is set. */
fun mavenCentralPublishAllowed(version: String, allowPre2: Boolean): Boolean {
    if (allowPre2) return true
    val base = version.substringBefore('-')
    val major = base.substringBefore('.').toIntOrNull() ?: return false
    return major >= 2
}

fun consumerSmokeMavenCentralVersion(): String =
    readSyncforgeVersion(rootProject.file("consumer-smoke/android-minimal/gradle.properties"))

tasks.register<Exec>("verifyConsumerSmoke") {
    group = "verification"
    description =
        "Publishes to mavenLocal, then compiles consumer-smoke/android-minimal from Maven coordinates only."
    dependsOn("publishAllToMavenLocal")
    workingDir = rootProject.file("consumer-smoke/android-minimal")
    commandLine(
        rootProject.file("gradlew").absolutePath,
        ":app:compileDebugKotlin",
        "-Psyncforge.version=${syncforgeLibraryVersion()}",
        "--no-daemon",
    )
}

tasks.register<Exec>("verifyConsumerSmokeMavenCentral") {
    group = "verification"
    description =
        "Compiles consumer-smoke/android-minimal using only Maven Central (no mavenLocal / includeBuild)."
    dependsOn("verifyConsumerSmokeMavenCentralArtifacts")
    workingDir = rootProject.file("consumer-smoke/android-minimal")
    commandLine(
        rootProject.file("gradlew").absolutePath,
        ":app:compileDebugKotlin",
        "-Psyncforge.consumerSmoke.useMavenLocal=false",
        "--no-daemon",
    )
}

tasks.register("verifySignOffMatrix") {
    group = "verification"
    description =
        "Pre-tag sign-off: verifyReleaseSignOff only. Maven Central checks run after portal Publish " +
            "via Actions → Verify Maven Central Release or verifyConsumerSmokeMavenCentral locally."
    dependsOn("verifyReleaseSignOff")
}

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description =
        "Publishes all SyncForge library modules to Maven Central (2.0.0+ only; set mavenCentralPublishing=true and credentials in ~/.gradle/gradle.properties)."
    dependsOn(
        ":syncforge:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-annotations:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-ksp:publishMavenPublicationToMavenCentralRepository",
        ":syncforge-persistence:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-network-ktor:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-transport-core:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-transport-supabase:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-transport-firebase:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-transport-graphql:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-store-room:publishReleasePublicationToMavenCentralRepository",
        ":syncforge-store-inmemory:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-integration-koin:publishReleasePublicationToMavenCentralRepository",
        ":syncforge-integration-hilt:publishReleasePublicationToMavenCentralRepository",
        ":syncforge-integration-opentelemetry:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-android-deps:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-bom:publishMavenPublicationToMavenCentralRepository",
        ":syncforge-catalog:publishMavenPublicationToMavenCentralRepository",
        gradle.includedBuild("syncforge-gradle-plugin")
            .task(":publishAllPublicationsToMavenCentralRepository"),
    )
    onlyIf {
        if (providers.gradleProperty("mavenCentralPublishing").orNull != "true") {
            return@onlyIf false
        }
        val allowPre2 = providers.gradleProperty("allowPre2MavenCentralPublish").orNull == "true"
        val version = syncforgeLibraryVersion()
        val allowed = mavenCentralPublishAllowed(version, allowPre2)
        if (!allowed) {
            logger.lifecycle(
                "Skipping publishAllToMavenCentral for $version — Maven Central publish is gated until 2.0.0 " +
                    "(override with -PallowPre2MavenCentralPublish=true for maintainers only).",
            )
        }
        allowed
    }
}

tasks.register("verifyPublishSigning") {
    group = "verification"
    description = "Fails if signAllPublications=true but no Sign tasks were created (unsigned Central publish)."
    onlyIf {
        providers.gradleProperty("signAllPublications").orNull == "true"
    }
    doLast {
        verifySigningConfigured()
    }
}

fun verifySigningConfigured() {
    val signTaskCount = subprojects.sumOf { project ->
        project.tasks.withType<org.gradle.plugins.signing.Sign>().count()
    }
    check(signTaskCount > 0) {
        "No Sign tasks were created; Maven Central publish would be unsigned"
    }
    logger.lifecycle("Publish signing wired for $signTaskCount Sign task(s)")
}