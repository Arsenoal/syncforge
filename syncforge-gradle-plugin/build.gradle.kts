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

group = readParentGradleProperty("syncforge.group") ?: "dev.syncforge"
version = readParentGradleProperty("syncforge.version") ?: "0.6.0-SNAPSHOT"

gradlePlugin {
    plugins {
        create("syncForgeAndroid") {
            id = "dev.syncforge.android"
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
}

// java-gradle-plugin registers the pluginMaven publication automatically.