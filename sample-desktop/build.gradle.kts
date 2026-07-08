plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
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