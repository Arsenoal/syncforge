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

fun signingProperty(name: String): String? =
    providers.gradleProperty(name).orNull?.trim()
        ?: readParentGradleProperty(name)?.trim()

afterEvaluate {
    val publishing = extensions.getByType<org.gradle.api.publish.PublishingExtension>()
    val publishingEnabled = providers.gradleProperty("mavenCentralPublishing")
        .map { it.toBoolean() }
        .orElse(false)
        .get()
    if (publishingEnabled) {
        publishing.repositories {
            maven {
                name = "MavenCentral"
                val isSnapshot = version.toString().endsWith("SNAPSHOT")
                url = uri(
                    if (isSnapshot) {
                        "https://central.sonatype.com/repository/maven-snapshots/"
                    } else {
                        "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                    },
                )
                credentials {
                    username = providers.gradleProperty("mavenCentralUsername").orNull
                        ?: readParentGradleProperty("mavenCentralUsername")
                    password = providers.gradleProperty("mavenCentralPassword").orNull
                        ?: readParentGradleProperty("mavenCentralPassword")
                }
            }
        }
    }

    val signingRequested = providers.gradleProperty("signAllPublications").orElse("false").get().toBoolean()
    if (!signingRequested) return@afterEvaluate

    val inMemoryKey = signingProperty("signing.inMemoryKey")
    val secretKeyRingFile = signingProperty("signing.secretKeyRingFile")
    val keyId = signingProperty("signing.inMemoryKeyId") ?: signingProperty("signing.keyId")
    val keyPassword = signingProperty("signing.inMemoryKeyPassword") ?: signingProperty("signing.password")

    if (inMemoryKey.isNullOrBlank() && secretKeyRingFile.isNullOrBlank()) {
        // Composite includeBuild may not see ORG_GRADLE_PROJECT_*; parent gradle.properties supplies CI signing.
        return@afterEvaluate
    }

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