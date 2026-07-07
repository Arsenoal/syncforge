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