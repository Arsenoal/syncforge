// Lowercase name avoids Gradle type-safe accessor filename/class casing bugs on Linux CI.
rootProject.name = "syncforge"

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
    ":syncforge-network-ktor",
    ":syncforge-transport-core",
    ":syncforge-transport-supabase",
    ":syncforge-transport-firebase",
    ":syncforge-transport-graphql",
    ":syncforge-store-room",
    ":syncforge-store-inmemory",
    ":syncforge-integration-koin",
    ":syncforge-integration-hilt",
    ":syncforge-android-deps",
    ":syncforge-bom",
    ":syncforge-catalog",
    ":syncforge-server",
    ":backend-starter",
    ":backend-starter-spring",
    ":backend-starter-graphql",
    ":mock-server",
    ":sample",
    ":sample-ios-shared",
    ":sample-desktop",
)