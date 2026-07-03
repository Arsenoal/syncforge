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

tasks.register<Exec>("verifyConsumerSmoke") {
    group = "verification"
    description =
        "Publishes to mavenLocal, then compiles consumer-smoke/android-minimal from Maven coordinates only."
    dependsOn("publishAllToMavenLocal")
    workingDir = rootProject.file("consumer-smoke/android-minimal")
    commandLine(
        rootProject.file("gradlew").absolutePath,
        ":app:compileDebugKotlin",
        "--no-daemon",
    )
}

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description = "Publishes all SyncForge library modules to Maven Central (set mavenCentralPublishing=true and credentials in ~/.gradle/gradle.properties)."
    dependsOn(
        ":syncforge:publish",
        ":syncforge-annotations:publish",
        ":syncforge-ksp:publish",
        ":syncforge-persistence:publish",
        ":syncforge-android-deps:publish",
        ":syncforge-bom:publish",
        gradle.includedBuild("syncforge-gradle-plugin").task(":publish"),
    )
    onlyIf {
        providers.gradleProperty("mavenCentralPublishing").orNull == "true"
    }
}

tasks.register("verifyPublishSigning") {
    group = "verification"
    description = "Fails if signAllPublications=true but no Sign tasks were created (unsigned Central publish)."
    dependsOn(
        ":syncforge:publish",
        ":syncforge-annotations:publish",
        ":syncforge-ksp:publish",
        ":syncforge-persistence:publish",
        ":syncforge-android-deps:publish",
        ":syncforge-bom:publish",
        gradle.includedBuild("syncforge-gradle-plugin").task(":publish"),
    )
    onlyIf {
        providers.gradleProperty("signAllPublications").orNull == "true"
    }
    doLast {
        val signTaskCount = subprojects.sumOf { project ->
            project.tasks.withType<org.gradle.plugins.signing.Sign>().count()
        }
        check(signTaskCount > 0) {
            "No Sign tasks were created; Maven Central publish would be unsigned"
        }
        logger.lifecycle("Publish signing wired for $signTaskCount Sign task(s)")
    }
}