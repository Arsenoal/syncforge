rootProject.name = "SyncForge"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("syncforge-gradle-plugin")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":syncforge",
    ":syncforge-annotations",
    ":syncforge-ksp",
    ":syncforge-persistence",
    ":syncforge-android-deps",
    ":syncforge-bom",
    ":mock-server",
    ":sample",
    ":sample-ios-shared",
)