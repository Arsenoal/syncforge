plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
    application
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

application {
    mainClass.set("dev.syncforge.sample.desktop.MainKt")
}

dependencies {
    implementation(project(":sample-ios-shared"))
    implementation(project(":syncforge"))
    implementation(project(":syncforge-network-ktor"))
    implementation(project(":syncforge-persistence"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.register<JavaExec>("runComposeConflictDemo") {
    group = "application"
    description = "Compose Desktop window demonstrating SyncConflictResolutionSheet (1.3-05)."
    mainClass.set("dev.syncforge.sample.desktop.ConflictComposeDemoKt")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
}