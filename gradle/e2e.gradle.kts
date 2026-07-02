import java.net.HttpURLConnection
import java.net.URL

fun Project.waitForMockServerHealth(port: Int, logFile: File, attempts: Int = 30) {
    val healthUrl = "http://127.0.0.1:$port/health"
    repeat(attempts) {
        runCatching {
            val connection = URL(healthUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.requestMethod = "GET"
            connection.connect()
            if (connection.responseCode in 200..299) {
                logger.lifecycle("Mock server healthy at $healthUrl")
                return
            }
        }
        Thread.sleep(1_000)
    }
    logger.error("Mock server failed to start. Log:")
    if (logFile.exists()) {
        logFile.readLines().takeLast(50).forEach { logger.error(it) }
    }
    error("Mock server did not become healthy at $healthUrl")
}

fun Project.startMockServer(port: Int, logFile: File): Process {
    val mockBin = file("mock-server/build/install/mock-server/bin/mock-server")
    require(mockBin.exists()) {
        "Mock server binary missing at ${mockBin.absolutePath} — run :mock-server:installDist first"
    }
    logger.lifecycle("Starting mock-server on port $port...")
    return ProcessBuilder(mockBin.absolutePath)
        .directory(rootDir)
        .redirectErrorStream(true)
        .redirectOutput(logFile)
        .start()
}

tasks.register("androidE2e") {
    group = "verification"
    description = "Runs sample connected Android tests against mock-server (requires emulator/device)."
    dependsOn(":mock-server:installDist")

    doLast {
        val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
        val logFile = File(System.getProperty("java.io.tmpdir"), "syncforge-mock-server.log")
        val process = startMockServer(port, logFile)
        try {
            waitForMockServerHealth(port, logFile)
            logger.lifecycle("Running connected Android tests...")
            exec {
                commandLine(
                    rootProject.file("gradlew").absolutePath,
                    ":sample:connectedDebugAndroidTest",
                    "--no-daemon",
                )
            }
        } finally {
            process.destroy()
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}

tasks.register("iosE2e") {
    group = "verification"
    description = "Runs ios-sample XCUITest UI tests against mock-server (requires macOS + Xcode Simulator)."
    dependsOn(":mock-server:installDist")

    doLast {
        if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            error("iOS UI tests require macOS with Xcode (Simulator + xcodebuild).")
        }

        val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
        val healthUrl = "http://127.0.0.1:$port/health"
        val logFile = File(System.getProperty("java.io.tmpdir"), "syncforge-mock-server.log")
        val process = startMockServer(port, logFile)
        try {
            waitForMockServerHealth(port, logFile)
            resetMockServerState(port)

            val kotlinTarget = if (System.getProperty("os.arch") == "aarch64") {
                "IosSimulatorArm64"
            } else {
                "IosX64"
            }
            logger.lifecycle("Pre-building Kotlin frameworks ($kotlinTarget)...")
            exec {
                commandLine(
                    rootProject.file("gradlew").absolutePath,
                    ":syncforge:linkDebugFramework$kotlinTarget",
                    ":sample-ios-shared:linkDebugFramework$kotlinTarget",
                    "--quiet",
                )
            }

            val destination = System.getenv("IOS_SIMULATOR_DESTINATION")
                ?: project.resolveIosSimulatorDestination()
            project.bootIosSimulatorForDestination(destination)
            logger.lifecycle("Running XCUITest on $destination...")

            val result = exec {
                isIgnoreExitValue = true
                environment("MOCK_SERVER_HEALTH_URL", healthUrl)
                environment("MOCK_SERVER_BASE_URL", "http://127.0.0.1:$port")
                commandLine(
                    "xcodebuild",
                    "test",
                    "-project",
                    "ios-sample/SyncForgeTasks.xcodeproj",
                    "-scheme",
                    "SyncForgeTasks",
                    "-destination",
                    destination,
                    "-only-testing:SyncForgeTasksUITests",
                    "-parallel-testing-enabled",
                    "NO",
                    "-maximum-concurrent-test-simulator-destinations",
                    "1",
                    "CODE_SIGNING_ALLOWED=NO",
                )
            }
            if (result.exitValue != 0) {
                logger.error("xcodebuild test failed (exit ${result.exitValue}). Recent mock-server log:")
                if (logFile.exists()) {
                    logFile.readLines().takeLast(30).forEach { logger.error(it) }
                }
                error("xcodebuild test failed with exit code ${result.exitValue}")
            }
        } finally {
            process.destroy()
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}

fun Project.resetMockServerState(port: Int) {
    val resetUrl = "http://127.0.0.1:$port/dev/reset"
    repeat(3) {
        runCatching {
            val connection = URL(resetUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connect()
            if (connection.responseCode in 200..299) {
                logger.lifecycle("Mock server reset at $resetUrl")
                return
            }
        }
        Thread.sleep(500)
    }
    logger.warn("Mock server reset at $resetUrl did not return 2xx (continuing)")
}

fun Project.bootIosSimulatorForDestination(destination: String) {
    val name = Regex("name=([^,]+)").find(destination)?.groupValues?.getOrNull(1) ?: return
    val udid = resolveIosSimulatorUdid(name) ?: run {
        logger.warn("Could not resolve simulator UDID for $name — skipping explicit boot")
        return
    }

    logger.lifecycle("Booting iOS Simulator $name ($udid) if needed...")
    exec {
        commandLine("xcrun", "simctl", "boot", udid)
        isIgnoreExitValue = true
    }
    exec {
        commandLine("xcrun", "simctl", "bootstatus", udid, "-b")
    }
}

fun Project.resolveIosSimulatorUdid(deviceName: String): String? {
    val listOutput = java.io.ByteArrayOutputStream()
    exec {
        commandLine("xcrun", "simctl", "list", "devices", "available")
        standardOutput = listOutput
    }
    val udidPattern = Regex("""\Q$deviceName\E \(([0-9A-Fa-f-]{36})\)""")
    return listOutput.toString().lineSequence()
        .mapNotNull { line -> udidPattern.find(line)?.groupValues?.getOrNull(1) }
        .firstOrNull()
}

fun Project.resolveIosSimulatorDestination(): String {
    val candidates = listOf("iPhone 16", "iPhone 15", "iPhone 14")
    val listOutput = java.io.ByteArrayOutputStream()
    exec {
        commandLine("xcrun", "simctl", "list", "devices", "available")
        standardOutput = listOutput
    }
    val deviceList = listOutput.toString()
    for (name in candidates) {
        if (deviceList.contains("$name (")) {
            return "platform=iOS Simulator,name=$name"
        }
    }
    return "platform=iOS Simulator,name=iPhone 16"
}