plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

application {
    mainClass.set("dev.syncforge.backendstarter.BackendStarterKt")
}

dependencies {
    implementation(project(":syncforge-server"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
}

kotlin {
    jvmToolchain(17)
}