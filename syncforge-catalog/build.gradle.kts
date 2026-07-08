plugins {
    `version-catalog`
    `maven-publish`
    signing
}

group = providers.gradleProperty("syncforge.group").get()
version = providers.gradleProperty("syncforge.version").get()

/** Library aliases published for consumers (mirrors syncforge-bom constraints). */
val catalogLibraries = listOf(
    "core" to "syncforge",
    "annotations" to "syncforge-annotations",
    "ksp" to "syncforge-ksp",
    "persistence" to "syncforge-persistence",
    "android-deps" to "syncforge-android-deps",
    "network-ktor" to "syncforge-network-ktor",
    "transport-core" to "syncforge-transport-core",
    "transport-supabase" to "syncforge-transport-supabase",
    "transport-firebase" to "syncforge-transport-firebase",
    "transport-graphql" to "syncforge-transport-graphql",
    "store-room" to "syncforge-store-room",
    "store-inmemory" to "syncforge-store-inmemory",
    "integration-koin" to "syncforge-integration-koin",
    "integration-hilt" to "syncforge-integration-hilt",
)

catalog {
    versionCatalog {
        val syncforgeGroup = group.toString()
        version("syncforge", version.toString())
        catalogLibraries.forEach { (alias, artifactId) ->
            library(alias, syncforgeGroup, artifactId).versionRef("syncforge")
        }
        plugin("syncforge-android", "studio.syncforge.android").versionRef("syncforge")
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["versionCatalog"])
            artifactId = project.name
        }
    }
}

val generateCatalogAsToml = tasks.named("generateCatalogAsToml")

tasks.register("verifyCatalogArtifacts") {
    group = "verification"
    description = "Fails unless all expected SyncForge library aliases are in the published catalog."
    dependsOn(generateCatalogAsToml)
    doLast {
        val expectedLibraries = catalogLibraries.map { it.first }.toSet()
        val toml = layout.buildDirectory.file("version-catalog/libs.versions.toml").get().asFile
        check(toml.exists()) { "Missing generated catalog TOML at ${toml.path}" }
        val librarySection = toml.readText()
            .substringAfter("[libraries]")
            .substringBefore("[plugins]")
        val found = Regex("""^([a-z0-9-]+)\s*=""", RegexOption.MULTILINE)
            .findAll(librarySection)
            .map { it.groupValues[1] }
            .toSet()
        val missing = expectedLibraries - found
        check(missing.isEmpty()) {
            "syncforge-catalog missing library aliases: ${missing.joinToString()}"
        }
        check(toml.readText().contains("syncforge-android = {id = \"studio.syncforge.android\"")) {
            "syncforge-catalog missing plugin alias syncforge-android"
        }
        check(toml.readText().contains("syncforge = \"${version}\"")) {
            "syncforge-catalog missing version syncforge=${version}"
        }
    }
}