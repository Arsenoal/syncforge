import co.touchlab.skie.configuration.FlowInterop
import co.touchlab.skie.configuration.SuspendInterop

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.skie)
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SyncForgeSample"
            isStatic = true
        }
    }

    sourceSets.all {
        languageSettings.optIn("dev.syncforge.api.ExperimentalSyncForgeApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":syncforge"))
            implementation(project(":syncforge-network-ktor"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        iosMain.dependencies {
            implementation(libs.skie.configuration.annotations)
        }
    }
}

skie {
    features {
        group("dev.syncforge.sample.ios") {
            FlowInterop.Enabled(true)
            SuspendInterop.Enabled(true)
        }
    }
}