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
    jvm()

    js(IR) {
        browser()
    }

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

    sourceSets {
        commonMain.dependencies {
            implementation(project(":syncforge"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(project(":syncforge-network-ktor"))
        }
        iosMain.dependencies {
            implementation(project(":syncforge-network-ktor"))
            implementation(libs.skie.configuration.annotations)
        }
    }

    jvmToolchain(17)
}

skie {
    features {
        group("dev.syncforge.sample.ios") {
            FlowInterop.Enabled(true)
            SuspendInterop.Enabled(true)
        }
    }
}