import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.syncforgeAndroid)
}

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
    val syncforgeVersion = libs.versions.syncforge.get()

    implementation(platform("dev.syncforge:syncforge-bom:$syncforgeVersion"))
    implementation("dev.syncforge:syncforge")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
}