plugins {
    `java-platform`
    `maven-publish`
    signing
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        val libraryVersion = project.version.toString()
        api("${project.group}:syncforge:$libraryVersion")
        api("${project.group}:syncforge-annotations:$libraryVersion")
        api("${project.group}:syncforge-ksp:$libraryVersion")
        api("${project.group}:syncforge-persistence:$libraryVersion")
        api("${project.group}:syncforge-android-deps:$libraryVersion")
        api("${project.group}:syncforge-network-ktor:$libraryVersion")
        api("${project.group}:syncforge-transport-core:$libraryVersion")
        api("${project.group}:syncforge-transport-supabase:$libraryVersion")
        api("${project.group}:syncforge-transport-firebase:$libraryVersion")
        api("${project.group}:syncforge-transport-graphql:$libraryVersion")
        api("${project.group}:syncforge-store-room:$libraryVersion")
        api("${project.group}:syncforge-store-inmemory:$libraryVersion")
        api("${project.group}:syncforge-integration-koin:$libraryVersion")
        api("${project.group}:syncforge-integration-hilt:$libraryVersion")
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = project.name
        }
    }
}

/** F1: optional 1.1 artifacts must be BOM constraints (not transitive in :syncforge). */
tasks.register("verifyBomConstraints") {
    group = "verification"
    description = "Fails unless all expected SyncForge library artifacts are listed as BOM constraints."
    doLast {
        val expected = setOf(
            "syncforge",
            "syncforge-annotations",
            "syncforge-ksp",
            "syncforge-persistence",
            "syncforge-android-deps",
            "syncforge-network-ktor",
            "syncforge-transport-core",
            "syncforge-transport-supabase",
            "syncforge-transport-firebase",
            "syncforge-transport-graphql",
            "syncforge-store-room",
            "syncforge-store-inmemory",
            "syncforge-integration-koin",
            "syncforge-integration-hilt",
        )
        val found = configurations.getByName("api")
            .allDependencyConstraints
            .map { it.name }
            .toSet()
        val missing = expected - found
        check(missing.isEmpty()) {
            "syncforge-bom missing constraints for: ${missing.joinToString()}"
        }
    }
}