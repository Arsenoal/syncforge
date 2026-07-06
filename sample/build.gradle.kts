import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    id("studio.syncforge.android")
}

android {
    namespace = "dev.syncforge.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.syncforge.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("syncforge.version").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SYNC_BASE_URL", "\"http://10.0.2.2:8080\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        animationsDisabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Experimental APIs (debug, etc.) opt in at call site — see @OptIn on demo/E2E helpers.
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("androidx.concurrent:concurrent-futures:1.2.0")
        force("androidx.concurrent:concurrent-futures-ktx:1.2.0")
        // API 35+ emulators require androidx.test 1.7+ / espresso 3.7+ / monitor 1.8+
        force("androidx.test:core:${libs.versions.androidx.test.core.get()}")
        force("androidx.test:runner:${libs.versions.androidx.test.runner.get()}")
        force("androidx.test:monitor:${libs.versions.androidx.test.monitor.get()}")
        force("androidx.test.espresso:espresso-core:${libs.versions.androidx.test.espresso.get()}")
    }
}

dependencies {
    implementation(project(":syncforge"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin {
    jvmToolchain(17)
}