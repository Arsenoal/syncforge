plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

application {
    mainClass.set("dev.syncforge.mockserver.MockSyncServerKt")
}

dependencies {
    implementation(project(":syncforge"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback.classic)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()
}