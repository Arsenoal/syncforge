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

fun Project.iosE2eMetadataDir(): File {
    val dir = layout.buildDirectory.dir("ios-e2e").get().asFile
    dir.mkdirs()
    return dir
}

fun Project.requireMacOsForIosE2e() {
    if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        error("iOS UI tests require macOS with Xcode (Simulator + xcodebuild).")
    }
}

fun Project.resolveIosE2ePort(): Int =
    System.getenv("PORT")?.toIntOrNull()
        ?: iosE2eMetadataDir().resolve("port.txt").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        ?: 8080

fun Project.resolveIosE2eDestination(): String =
    System.getenv("IOS_SIMULATOR_DESTINATION")?.trim()?.takeIf { it.isNotEmpty() }
        ?: iosE2eMetadataDir().resolve("destination.txt").takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        ?: resolveIosSimulatorDestination()

fun Project.runIosXcuiTest(destination: String, port: Int, mockServerLog: File? = null) {
    val healthUrl = "http://127.0.0.1:$port/health"
    logger.lifecycle("Using Swift-only E2E stub (no KMP frameworks linked for XCUITest)")
    logger.lifecycle("Running XCUITest on $destination...")

    val result = exec {
        isIgnoreExitValue = true
        environment("MOCK_SERVER_HEALTH_URL", healthUrl)
        environment("MOCK_SERVER_BASE_URL", "http://127.0.0.1:$port")
        environment("E2E_TESTING", "1")
        environment("E2E_SWIFT_STUB", "1")
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
            "E2E_SWIFT_STUB=1",
            "SWIFT_ACTIVE_COMPILATION_CONDITIONS=E2E_SWIFT_STUB",
            "GCC_PREPROCESSOR_DEFINITIONS=E2E_SWIFT_STUB=1",
            "OTHER_LDFLAGS=-lsqlite3",
        )
    }
    if (result.exitValue != 0) {
        logger.error("xcodebuild test failed (exit ${result.exitValue}). Recent mock-server log:")
        mockServerLog?.takeIf { it.exists() }?.readLines()?.takeLast(30)?.forEach { logger.error(it) }
        error("xcodebuild test failed with exit code ${result.exitValue}")
    }
}

tasks.register("iosE2ePrepareSimulator") {
    group = "verification"
    description = "Resolves an iOS Simulator destination, boots it, and writes build/ios-e2e metadata for CI steps."

    doLast {
        requireMacOsForIosE2e()
        val destination = resolveIosE2eDestination()
        bootIosSimulatorForDestination(destination)

        val metadata = iosE2eMetadataDir()
        metadata.resolve("destination.txt").writeText(destination)
        metadata.resolve("port.txt").writeText(resolveIosE2ePort().toString())
        logger.lifecycle("iOS E2E destination: $destination")
        logger.lifecycle("iOS E2E metadata: ${metadata.absolutePath}")
    }
}

tasks.register("iosE2eXcuiTest") {
    group = "verification"
    description = "Runs XCUITest against a mock-server that is already running (see ios-e2e CI workflow)."

    doLast {
        requireMacOsForIosE2e()
        val port = resolveIosE2ePort()
        val logFile = iosE2eMetadataDir().resolve("mock-server.log")
        waitForMockServerHealth(port, logFile)
        resetMockServerState(port)
        runIosXcuiTest(resolveIosE2eDestination(), port, logFile)
    }
}

tasks.register("iosE2e") {
    group = "verification"
    description = "Runs ios-sample XCUITest UI tests against mock-server (requires macOS + Xcode Simulator)."
    dependsOn(":mock-server:installDist", "iosE2ePrepareSimulator")

    doLast {
        requireMacOsForIosE2e()
        val port = resolveIosE2ePort()
        val logFile = iosE2eMetadataDir().resolve("mock-server.log")
        val process = startMockServer(port, logFile)
        try {
            waitForMockServerHealth(port, logFile)
            resetMockServerState(port)
            runIosXcuiTest(resolveIosE2eDestination(), port, logFile)
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

data class IosSimulatorDevice(
    val name: String,
    val udid: String,
    val runtime: String,
)

fun Project.listIosSimulators(): List<IosSimulatorDevice> {
    val listOutput = java.io.ByteArrayOutputStream()
    exec {
        commandLine("xcrun", "simctl", "list", "devices", "available")
        standardOutput = listOutput
    }

    val devices = mutableListOf<IosSimulatorDevice>()
    var currentRuntime = ""
    val runtimePattern = Regex("""-- (.+) --""")
    val devicePattern = Regex("""^\s+(.+?) \(([0-9A-Fa-f-]{36})\)""")

    listOutput.toString().lineSequence().forEach { line ->
        runtimePattern.find(line)?.let {
            currentRuntime = it.groupValues[1]
            return@forEach
        }
        devicePattern.find(line)?.let { match ->
            devices += IosSimulatorDevice(
                name = match.groupValues[1],
                udid = match.groupValues[2],
                runtime = currentRuntime,
            )
        }
    }
    return devices
}

fun Project.bootIosSimulatorForDestination(destination: String) {
    val name = Regex("""name=([^,]+)""").find(destination)?.groupValues?.getOrNull(1) ?: return
    val os = Regex("""OS=([^,]+)""").find(destination)?.groupValues?.getOrNull(1)
    val udid = resolveIosSimulatorUdid(name, os) ?: run {
        logger.warn("Could not resolve simulator UDID for $name (OS=$os) — skipping explicit boot")
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

fun Project.resolveIosSimulatorUdid(deviceName: String, osVersion: String? = null): String? {
    val devices = listIosSimulators()
    if (osVersion != null) {
        val runtime = devices.firstOrNull { it.name == deviceName && it.runtime.endsWith(osVersion) }
        if (runtime != null) {
            return runtime.udid
        }
    }
    return devices.firstOrNull { it.name == deviceName }?.udid
}

fun Project.resolveIosSimulatorDestination(): String {
    val devices = listIosSimulators()
    val preferredRuntimes = listOf("17.5", "17.4", "17.2", "17.0", "17.1")
    val preferredNames = listOf("iPhone 15", "iPhone 14", "iPhone SE (3rd generation)", "iPhone 16")

    for (os in preferredRuntimes) {
        for (name in preferredNames) {
            val match = devices.firstOrNull { it.name == name && it.runtime.contains(os) }
            if (match != null) {
                return "platform=iOS Simulator,name=${match.name},OS=$os"
            }
        }
    }

    val fallback = devices.firstOrNull { it.name.startsWith("iPhone") }
    if (fallback != null) {
        val os = Regex("""\d+\.\d+""").find(fallback.runtime)?.value
        return if (os != null) {
            "platform=iOS Simulator,name=${fallback.name},OS=$os"
        } else {
            "platform=iOS Simulator,name=${fallback.name}"
        }
    }
    return "platform=iOS Simulator,name=iPhone 15,OS=17.5"
}