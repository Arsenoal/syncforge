import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    `maven-publish`
    signing
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

android {
    namespace = "dev.syncforge.integration.koin"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":syncforge"))
    implementation(libs.koin.android)
}

afterEvaluate {
    extensions.configure<PublishingExtension>("publishing") {
        if (publications.none { it.name == "release" }) {
            publications.create<MavenPublication>("release") {
                from(components["release"])
                artifactId = project.name
            }
        }
    }
}