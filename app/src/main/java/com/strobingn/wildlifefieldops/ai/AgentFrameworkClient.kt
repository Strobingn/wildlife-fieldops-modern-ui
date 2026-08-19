package com.strobingn.wildlifefieldops.ai

import android.util.Log
import com.strobingn.wildlifefieldops.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for the Microsoft Agent Framework sidecar
 * (`agents/fieldops-maf`). The native app never embeds MAF itself.
 */
object AgentFrameworkClient {
    private const val TAG = "AgentFramework"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class RunRequest(
        val message: String,
        val agent: String = "orchestrator",
        val species: String = "",
        val context: Map<String, String> = emptyMap()
    )

    @Serializable
    private data class RunResponse(
        val text: String = "",
        val agent: String = "",
        val backend: String = "",
        val tools_used: List<String> = emptyList(),
        val workflow: List<String> = emptyList()
    )

    val endpoint: String
        get() = BuildConfig.AGENT_FRAMEWORK_URL.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = endpoint.startsWith("http://") || endpoint.startsWith("https://")

    fun diagnostics(): String = buildString {
        append("Microsoft Agent Framework sidecar\n")
        append("URL: ${endpoint.ifBlank { "(not set — using Grok/offline)" }}\n")
        append("Enabled: $isConfigured")
    }

    fun isReachable(): Boolean {
        if (!isConfigured) return false
        return runCatching {
            val connection = (URL("$endpoint/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 2_500
            }
            try {
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    fun runOrNull(
        userMessage: String,
        species: String = "",
        agent: String = "orchestrator",
        context: Map<String, String> = emptyMap()
    ): String? {
        if (!isConfigured) return null
        return runCatching {
            val connection = (URL("$endpoint/v1/run").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                val payload = json.encodeToString(
                    RunRequest(
                        message = userMessage,
                        agent = agent,
                        species = species,
                        context = context
                    )
                )
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                if (code !in 200..299) {
                    Log.w(TAG, "MAF HTTP $code: ${body.take(240)}")
                    return null
                }
                val parsed = json.decodeFromString(RunResponse.serializer(), body)
                val text = parsed.text.trim()
                if (text.isEmpty()) return null
                buildString {
                    append(text)
                    if (parsed.backend.isNotBlank()) {
                        append("\n\n— ")
                        append(parsed.backend)
                        if (parsed.agent.isNotBlank()) append(" / ").append(parsed.agent)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "MAF request failed", it) }.getOrNull()
    }
}
