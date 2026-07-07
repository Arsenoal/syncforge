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
}

apply(from = "gradle/publish-convention.gradle.kts")
apply(from = "gradle/e2e.gradle.kts")
apply(from = "gradle/ios-xcode.gradle.kts")

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes all SyncForge library modules to the local Maven repository."
    dependsOn(
        ":syncforge:publishToMavenLocal",
        ":syncforge-annotations:publishToMavenLocal",
        ":syncforge-ksp:publishToMavenLocal",
        ":syncforge-persistence:publishToMavenLocal",
        ":syncforge-network-ktor:publishToMavenLocal",
        ":syncforge-android-deps:publishToMavenLocal",
        ":syncforge-bom:publishToMavenLocal",
        gradle.includedBuild("syncforge-gradle-plugin").task(":publishToMavenLocal"),
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
        ":syncforge-server:test",
        ":syncforge-persistence:compileDebugKotlinAndroid",
        ":syncforge-persistence:compileKotlinJvm",
        ":syncforge-annotations:compileDebugKotlinAndroid",
        ":syncforge-annotations:compileKotlinJvm",
        ":syncforge-ksp:compileKotlin",
        ":backend-starter:compileKotlin",
        ":mock-server:compileKotlin",
        ":sample:compileDebugKotlin",
        ":sample:compileDebugAndroidTestKotlin",
        "verifyConsumerSmoke",
    )
}

fun readSyncforgeVersion(propertiesFile: java.io.File): String {
    val props = java.util.Properties()
    propertiesFile.inputStream().use { props.load(it) }
    return props.getProperty("syncforge.version")
        ?: error("syncforge.version missing in ${propertiesFile.path}")
}

fun syncforgeLibraryVersion(): String = readSyncforgeVersion(rootProject.file("gradle.properties"))

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

tasks.register<Exec>("verifyConsumerSmokeMavenCentralArtifacts") {
    group = "verification"
    description =
        "Fails unless required SyncForge POMs resolve on repo1.maven.org for the consumer-smoke Maven Central pin."
    commandLine(
        rootProject.file(".github/scripts/verify-maven-central-artifacts.sh").absolutePath,
        consumerSmokeMavenCentralVersion(),
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

/** Required Maven Central coordinates for a complete consumer-facing release. */
val mavenCentralRequiredArtifacts = listOf(
    "syncforge",
    "syncforge-android",
    "syncforge-jvm",
    "syncforge-annotations",
    "syncforge-persistence",
    "syncforge-android-deps",
    "syncforge-network-ktor",
    "syncforge-bom",
    "syncforge-ksp",
    "syncforge-gradle-plugin",
)

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description =
        "Publishes all SyncForge library modules to Maven Central (set mavenCentralPublishing=true and credentials in ~/.gradle/gradle.properties)."
    dependsOn(
        ":syncforge:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-annotations:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-ksp:publishMavenPublicationToMavenCentralRepository",
        ":syncforge-persistence:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-network-ktor:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-android-deps:publishAllPublicationsToMavenCentralRepository",
        ":syncforge-bom:publishMavenPublicationToMavenCentralRepository",
        gradle.includedBuild("syncforge-gradle-plugin")
            .task(":publishAllPublicationsToMavenCentralRepository"),
    )
    onlyIf {
        providers.gradleProperty("mavenCentralPublishing").orNull == "true"
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

tasks.register<Exec>("verifyMavenCentralArtifacts") {
    group = "verification"
    description =
        "Fails unless required SyncForge POMs resolve on repo1.maven.org for syncforge.version."
    commandLine(
        rootProject.file(".github/scripts/verify-maven-central-artifacts.sh").absolutePath,
        syncforgeLibraryVersion(),
    )
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