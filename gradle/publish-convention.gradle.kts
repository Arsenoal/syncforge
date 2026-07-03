import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

val publishableModules = setOf(
    "syncforge",
    "syncforge-annotations",
    "syncforge-ksp",
    "syncforge-persistence",
    "syncforge-android-deps",
    "syncforge-bom",
)

fun Project.javadocJarTask(): org.gradle.api.tasks.TaskProvider<Jar> {
    val dokkaTask = tasks.findByName("dokkaGeneratePublicationJavadoc")
    return if (dokkaTask != null) {
        tasks.register<Jar>("dokkaJavadocJar") {
            archiveClassifier.set("javadoc")
            dependsOn(dokkaTask)
            from(dokkaTask.outputs.files.asFileTree)
        }
    } else {
        tasks.register<Jar>("emptyJavadocJar") {
            archiveClassifier.set("javadoc")
            from("${rootProject.projectDir}/LICENSE") {
                rename { "README.txt" }
            }
        }
    }
}

gradle.projectsEvaluated {
    subprojects
        .filter { it.name in publishableModules }
        .forEach { project ->
            if (!project.pluginManager.hasPlugin("maven-publish")) return@forEach

            val licenseName = project.providers.gradleProperty("syncforge.license.name")
            val licenseUrl = project.providers.gradleProperty("syncforge.license.url")
            val pomUrl = project.providers.gradleProperty("syncforge.pom.url")
            val scmConnection = project.providers.gradleProperty("syncforge.pom.scm.connection")
            val scmDeveloperConnection = project.providers.gradleProperty("syncforge.pom.scm.developerConnection")
            val pomInceptionYear = project.providers.gradleProperty("syncforge.pom.inceptionYear")
            val descriptionProperty = "syncforge.pom.description.${project.name}"
            val moduleDescription = project.findProperty(descriptionProperty)?.toString()
                ?: "SyncForge — offline-first sync library for Kotlin Multiplatform"

            val javadocJar = project.javadocJarTask()

            project.extensions.configure<PublishingExtension>("publishing") {
                if (publications.isEmpty()) {
                    val component = components.findByName("java")
                        ?: components.findByName("kotlin")
                        ?: components.findByName("release")
                    if (component != null) {
                        publications.create<MavenPublication>("maven") {
                            from(component)
                            artifactId = project.name
                        }
                    }
                }

                publications.withType<MavenPublication>().configureEach {
                    groupId = project.group.toString()
                    version = project.version.toString()
                    if (artifactId.isBlank()) {
                        artifactId = project.name
                    }

                    val hasJavadoc = artifacts.any { it.classifier == "javadoc" }
                    if (!hasJavadoc) {
                        artifact(javadocJar)
                    }

                    pom {
                        name.set(project.name)
                        description.set(project.provider { moduleDescription })
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

                val publishingEnabled = project.providers.gradleProperty("mavenCentralPublishing")
                    .map { it.toBoolean() }
                    .orElse(false)

                if (publishingEnabled.get()) {
                    repositories {
                        maven {
                            name = "MavenCentral"
                            val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
                            url = uri(
                                if (isSnapshot) {
                                    "https://central.sonatype.com/repository/maven-snapshots/"
                                } else {
                                    "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                                },
                            )
                            credentials {
                                username = project.providers.gradleProperty("mavenCentralUsername").orNull
                                password = project.providers.gradleProperty("mavenCentralPassword").orNull
                            }
                        }
                    }
                }
            }

            configurePublicationSigning(project)
        }
}

fun configurePublicationSigning(project: Project) {
    val signingRequested = project.providers.gradleProperty("signAllPublications")
        .map { it.toBoolean() }
        .orElse(false)

    fun wireSigning() {
        if (!signingRequested.get()) return

        val inMemoryKey = project.providers.gradleProperty("signing.inMemoryKey").orNull?.trim()
        val keyId = project.providers.gradleProperty("signing.inMemoryKeyId").orNull?.trim()
        val keyPassword = project.providers.gradleProperty("signing.inMemoryKeyPassword").orNull
        val legacyKeyId = project.providers.gradleProperty("signing.keyId").orNull?.trim()
        check(!inMemoryKey.isNullOrBlank() || !legacyKeyId.isNullOrBlank()) {
            "${project.path}: signAllPublications=true but no signing.inMemoryKey or signing.keyId is configured"
        }

        val publishing = project.extensions.getByType<PublishingExtension>()
        val signing = project.extensions.getByType<SigningExtension>()
        if (!inMemoryKey.isNullOrBlank()) {
            signing.useInMemoryPgpKeys(keyId.orEmpty(), inMemoryKey, keyPassword.orEmpty())
        }
        signing.setRequired(true)
        publishing.publications.withType<MavenPublication>().configureEach {
            signing.sign(this)
        }
        project.tasks.withType<PublishToMavenRepository>().configureEach {
            dependsOn(project.tasks.withType<Sign>())
        }
    }

    wireSigning()
    project.gradle.taskGraph.whenReady { wireSigning() }
}