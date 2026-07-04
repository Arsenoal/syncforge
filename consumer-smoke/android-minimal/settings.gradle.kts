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