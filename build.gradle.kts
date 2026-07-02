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

tasks.register<Exec>("androidE2e") {
    group = "verification"
    description = "Runs sample connected Android tests against mock-server (requires emulator/device)."
    commandLine("bash", "${rootProject.projectDir}/scripts/run-android-e2e.sh")
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