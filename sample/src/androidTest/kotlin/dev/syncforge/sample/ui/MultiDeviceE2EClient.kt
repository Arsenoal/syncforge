package dev.syncforge.sample.ui

import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URL

/** HTTP client for mock-server `/dev/e2e/session` coordination (1.4-06). */
object MultiDeviceE2EClient {

    private fun baseUrl(): String =
        InstrumentationRegistry.getArguments().getString("mockServerUrl")
            ?: "http://10.0.2.2:8080"

    fun createSession(sessionId: String) {
        post("/dev/e2e/session", """{"sessionId":"$sessionId"}""")
    }

    fun put(sessionId: String, key: String, value: String) {
        post(
            "/dev/e2e/session/$sessionId",
            """{"key":${key.jsonString()},"value":${value.jsonString()}}""",
        )
    }

    fun waitForKey(
        sessionId: String,
        key: String,
        timeoutMillis: Long = 60_000,
        pollMillis: Long = 500,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            snapshot(sessionId)[key]?.let { return it }
            Thread.sleep(pollMillis)
        }
        error("Timed out waiting for session $sessionId key=$key")
    }

    fun snapshot(sessionId: String): Map<String, String> {
        val connection = openGet("/dev/e2e/session/$sessionId")
        val body = connection.inputStream.bufferedReader().readText()
        check(connection.responseCode in 200..299) {
            "GET session failed: ${connection.responseCode} $body"
        }
        return parseValues(body)
    }

    private fun parseValues(json: String): Map<String, String> {
        val valuesStart = json.indexOf("\"values\":{")
        if (valuesStart < 0) return emptyMap()
        val chunk = json.substring(valuesStart + "\"values\":{".length)
        val end = chunk.indexOf('}')
        if (end < 0) return emptyMap()
        val inner = chunk.substring(0, end).trim()
        if (inner.isEmpty()) return emptyMap()
        return inner.split(',')
            .mapNotNull { entry ->
                val parts = entry.split(':', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val key = parts[0].trim().trim('"')
                val value = parts[1].trim().trim('"')
                key to value
            }
            .toMap()
    }

    private fun String.jsonString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun post(path: String, body: String) {
        val connection = openPost(path)
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText()
            error("POST $path failed: $code $errorBody")
        }
    }

    private fun openPost(path: String): HttpURLConnection =
        (URL("${baseUrl()}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 5_000
            readTimeout = 5_000
        }

    private fun openGet(path: String): HttpURLConnection =
        (URL("${baseUrl()}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }
}