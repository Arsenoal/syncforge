import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("studio.syncforge.android")
}

val syncforgeVersion =
    findProperty("syncforge.version") as String? ?: libs.versions.syncforge.get()

android {
    namespace = "dev.syncforge.consumer.smoke"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.syncforge.consumer.smoke"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("studio.syncforge:syncforge-bom:$syncforgeVersion"))
    implementation("studio.syncforge:syncforge")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
}