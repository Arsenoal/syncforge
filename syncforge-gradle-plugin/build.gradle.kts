import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
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

val pluginSourcesJar = tasks.register<Jar>("pluginSourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val pluginJavadocJar = tasks.register<Jar>("pluginJavadocJar") {
    archiveClassifier.set("javadoc")
    from(file("../LICENSE")) {
        rename { "README.txt" }
    }
}

fun signingProperty(name: String): String? =
    providers.gradleProperty(name).orNull?.trim()
        ?: readParentGradleProperty(name)?.trim()

afterEvaluate {
    val publishing = extensions.getByType<org.gradle.api.publish.PublishingExtension>()
    val licenseName = readParentGradleProperty("syncforge.license.name")
    val licenseUrl = readParentGradleProperty("syncforge.license.url")
    val pomUrl = readParentGradleProperty("syncforge.pom.url")
    val scmConnection = readParentGradleProperty("syncforge.pom.scm.connection")
    val scmDeveloperConnection = readParentGradleProperty("syncforge.pom.scm.developerConnection")
    val pomInceptionYear = readParentGradleProperty("syncforge.pom.inceptionYear")
    val pluginDescription = readParentGradleProperty("syncforge.pom.description.syncforge-gradle-plugin")
        ?: "Gradle plugin wiring SyncForge KSP and Kotlin serialization for Android apps"

    publishing.publications.named<MavenPublication>("pluginMaven") {
        val hasSources = artifacts.any { it.classifier == "sources" }
        val hasJavadoc = artifacts.any { it.classifier == "javadoc" }
        if (!hasSources) artifact(pluginSourcesJar)
        if (!hasJavadoc) artifact(pluginJavadocJar)
        pom {
            name.set("syncforge-gradle-plugin")
            description.set(pluginDescription)
            inceptionYear.set(pomInceptionYear)
            url.set(pomUrl)
            licenses {
                license {
                    name.set(licenseName)
                    url.set(licenseUrl)
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    name.set("SyncForge Contributors")
                }
            }
            scm {
                connection.set(scmConnection)
                developerConnection.set(scmDeveloperConnection)
                url.set(pomUrl)
            }
        }
    }

    publishing.publications.named<MavenPublication>("syncForgeAndroidPluginMarkerMaven") {
        pom {
            inceptionYear.set(pomInceptionYear)
            url.set(pomUrl)
            licenses {
                license {
                    name.set(licenseName)
                    url.set(licenseUrl)
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    name.set("SyncForge Contributors")
                }
            }
            scm {
                connection.set(scmConnection)
                developerConnection.set(scmDeveloperConnection)
                url.set(pomUrl)
            }
        }
    }

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
        return@afterEvaluate
    }

    val signingExtension = extensions.getByType<SigningExtension>()
    if (!inMemoryKey.isNullOrBlank()) {
        signingExtension.useInMemoryPgpKeys(keyId.orEmpty(), inMemoryKey, keyPassword.orEmpty())
    }
    signingExtension.setRequired(true)
    publishing.publications.withType<MavenPublication>().configureEach {
        signingExtension.sign(this)
    }
    tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}