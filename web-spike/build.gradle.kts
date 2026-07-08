plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "web-spike-js.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.web.worker.driver)
            implementation(libs.sqldelight.async.extensions)
            implementation(libs.sqldelight.coroutines)
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            implementation(devNpm("@cashapp/sqldelight-sqljs-worker", "2.0.2"))
        }
    }
}

sqldelight {
    databases {
        create("WebSpikeDatabase") {
            packageName.set("dev.syncforge.webspike.persistence")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            generateAsync.set(true)
        }
    }
}