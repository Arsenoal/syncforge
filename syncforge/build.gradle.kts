import co.touchlab.skie.configuration.FlowInterop
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.skie)
    `maven-publish`
    signing
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        publishLibraryVariants("release")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SyncForge"
            isStatic = true
        }
    }

    listOf(
        macosArm64(),
        macosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SyncForge"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets.all {
        languageSettings.optIn("dev.syncforge.api.ExperimentalSyncForgeApi")
    }

    sourceSets {
        val syncPersistenceMain by creating {
            dependsOn(commonMain.get())
        }

        androidMain {
            dependsOn(syncPersistenceMain)
        }
        iosMain {
            dependsOn(syncPersistenceMain)
        }
        macosMain {
            dependsOn(syncPersistenceMain)
            dependsOn(iosMain.get())
        }
        jvmMain {
            dependsOn(syncPersistenceMain)
        }

        commonMain.dependencies {
            api(project(":syncforge-annotations"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        syncPersistenceMain.dependencies {
            implementation(project(":syncforge-persistence"))
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            api(project(":syncforge-android-deps"))
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation("androidx.compose.foundation:foundation-layout:${libs.versions.compose.ui.get()}")
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Satisfy Compose compiler on JVM (androidMain uses androidx; desktop does not use Compose UI).
            compileOnly(libs.jetbrains.compose.runtime)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.skie.configuration.annotations)
            // Satisfy Compose compiler on Kotlin/Native (Compose UI is androidMain-only).
            implementation(libs.jetbrains.compose.runtime)
        }
    }
}

skie {
    features {
        group("dev.syncforge") {
            FlowInterop.Enabled(true)
            SuspendInterop.Enabled(true)
        }
        group("dev.syncforge.sample") {
            FlowInterop.Enabled(true)
            SuspendInterop.Enabled(true)
        }
    }
}

android {
    namespace = "dev.syncforge"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}




