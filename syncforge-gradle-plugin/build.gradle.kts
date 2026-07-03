import java.util.Properties

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
}

fun readParentGradleProperty(name: String): String? {
    val props = Properties()
    val file = file("../gradle.properties")
    if (!file.exists()) return null
    file.reader().use { props.load(it) }
    return props.getProperty(name)
}

group = readParentGradleProperty("syncforge.group") ?: "studio.syncforge"
version = readParentGradleProperty("syncforge.version") ?: "0.6.0-SNAPSHOT"

gradlePlugin {
    plugins {
        create("syncForgeAndroid") {
            id = "studio.syncforge.android"
            implementationClass = "dev.syncforge.gradle.SyncForgeAndroidPlugin"
            displayName = "SyncForge Android"
            description =
                "Applies KSP (SyncForge + Room compiler) and Kotlin serialization for Android apps"
        }
    }
}

dependencies {
    implementation(gradleKotlinDsl())
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.10-1.0.31")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
    implementation(
        "org.jetbrains.kotlin.plugin.serialization:" +
            "org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.1.10",
    )
}

// java-gradle-plugin registers the pluginMaven publication automatically.

afterEvaluate {
    val signingRequested = providers.gradleProperty("signAllPublications").orElse("false").get().toBoolean()
    if (!signingRequested) return@afterEvaluate

    val inMemoryKey = providers.gradleProperty("signing.inMemoryKey").orNull?.trim()
    val secretKeyRingFile = providers.gradleProperty("signing.secretKeyRingFile").orNull?.trim()
    val keyId = providers.gradleProperty("signing.inMemoryKeyId").orNull?.trim()
        ?: providers.gradleProperty("signing.keyId").orNull?.trim()
    val keyPassword = providers.gradleProperty("signing.inMemoryKeyPassword").orNull
        ?: providers.gradleProperty("signing.password").orNull
    check(
        !inMemoryKey.isNullOrBlank() ||
            !secretKeyRingFile.isNullOrBlank() ||
            !keyId.isNullOrBlank(),
    ) {
        "syncforge-gradle-plugin: signAllPublications=true but no signing key is configured"
    }

    val publishing = extensions.getByType<org.gradle.api.publish.PublishingExtension>()
    val signingExtension = extensions.getByType<org.gradle.plugins.signing.SigningExtension>()
    if (!inMemoryKey.isNullOrBlank()) {
        signingExtension.useInMemoryPgpKeys(keyId.orEmpty(), inMemoryKey, keyPassword.orEmpty())
    }
    signingExtension.setRequired(true)
    publishing.publications.withType<org.gradle.api.publish.maven.MavenPublication>().configureEach {
        signingExtension.sign(this)
    }
    tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
        dependsOn(tasks.withType<org.gradle.plugins.signing.Sign>())
    }
}