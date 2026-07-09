import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(syncforge.plugins.syncforge.android)
}

android {
    namespace = "dev.syncforge.consumer.smoke"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.syncforge.consumer.smoke"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0"
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
    implementation(syncforge.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
}