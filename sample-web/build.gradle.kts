plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "sample-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets.all {
        languageSettings.optIn("dev.syncforge.api.ExperimentalSyncForgeApi")
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":sample-ios-shared"))
            implementation(project(":syncforge"))
            implementation(project(":syncforge-persistence"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.web.worker.driver)
            implementation(libs.sqldelight.async.extensions)
            implementation(libs.sqldelight.coroutines)
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            implementation(devNpm("@cashapp/sqldelight-sqljs-worker", "2.0.2"))
            implementation(devNpm("path-browserify", "1.0.1"))
            implementation(devNpm("crypto-browserify", "3.12.1"))
            implementation(devNpm("stream-browserify", "3.0.0"))
            implementation(devNpm("buffer", "6.0.3"))
            implementation(npm("sql.js", "1.12.0"))
        }
    }
}