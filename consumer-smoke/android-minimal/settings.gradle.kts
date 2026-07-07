pluginManagement {
    repositories {
        val useMavenLocal = providers.gradleProperty("syncforge.consumerSmoke.useMavenLocal")
            .orElse("true")
            .get()
            .toBoolean()
        if (useMavenLocal) {
            mavenLocal()
        }
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "studio.syncforge.android") {
                val version = providers.gradleProperty("syncforge.version")
                    .orElse("1.0.0")
                    .get()
                useModule("studio.syncforge.android:studio.syncforge.android.gradle.plugin:$version")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val useMavenLocal = providers.gradleProperty("syncforge.consumerSmoke.useMavenLocal")
            .orElse("true")
            .get()
            .toBoolean()
        if (useMavenLocal) {
            mavenLocal()
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "syncforge-consumer-smoke-android"

include(":app")