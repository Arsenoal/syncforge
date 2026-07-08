fun readSyncforgeVersionFromProperties(): String {
    val props = java.util.Properties()
    rootProject.file("gradle.properties").inputStream().use { props.load(it) }
    return props.getProperty("syncforge.version")
        ?: error("syncforge.version missing in gradle.properties")
}

/** iOS SPM / XCFramework publish is gated to 2.0.0+ unless [allowPre2IosSpmPublish] is set. */
fun iosSpmPublishAllowed(version: String, allowPre2: Boolean): Boolean {
    if (allowPre2) return true
    val base = version.substringBefore('-')
    val major = base.substringBefore('.').toIntOrNull() ?: return false
    return major >= 2
}

tasks.register("publishIosSpmArtifacts") {
    group = "publishing"
    description =
        "Publishes iOS SPM package and XCFramework zip (2.0.0+ only; set iosSpmPublishing=true). " +
            "Implementation tracked in roadmap 1.3-04."
    onlyIf {
        if (providers.gradleProperty("iosSpmPublishing").orNull != "true") {
            return@onlyIf false
        }
        val allowPre2 = providers.gradleProperty("allowPre2IosSpmPublish").orNull == "true"
        val version = readSyncforgeVersionFromProperties()
        val allowed = iosSpmPublishAllowed(version, allowPre2)
        if (!allowed) {
            logger.lifecycle(
                "Skipping publishIosSpmArtifacts for $version — iOS SPM/XCFramework publish is gated until 2.0.0 " +
                    "(override with -PallowPre2IosSpmPublish=true for maintainers only).",
            )
        }
        allowed
    }
    doLast {
        logger.lifecycle(
            "publishIosSpmArtifacts: SPM/XCFramework pipeline not implemented yet (1.3-04). " +
                "Until 2.0, integrate iOS via KMP frameworks — see docs/IOS_SETUP.md and linkIosFrameworksForXcode.",
        )
    }
}