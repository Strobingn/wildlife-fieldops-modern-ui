package com.strobingn.wildlifefieldops.data.remote

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Grok calls go to the Supabase `ai-assistant` function.
 * Authorization is the signed-in user session when one exists, otherwise the
 * anon or publishable key already used by the rest of the client.
 */
@Singleton
class AiEdgeGateway @Inject constructor(
    private val supabase: SupabaseService
) {
    val isConfigured: Boolean get() = supabase.isConfigured

    fun complete(
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Double,
        jsonMode: Boolean = false
    ): EdgeChatResult {
        if (!isConfigured) {
            return EdgeChatResult.Err("Cloud AI needs Supabase configured in this build. On-device and manual entry still work.")
        }
        val endpoint = URL(supabase.edgeFunctionUrl("ai-assistant"))
        val payload = AiEdgeProtocol.requestJson(system, user, maxTokens, temperature, jsonMode)
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", supabase.anonOrPublishableKey)
            setRequestProperty("Authorization", "Bearer ${supabase.edgeAuthorizationBearer()}")
        }
        return try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            AiEdgeProtocol.parse(code, body)
        } catch (e: Exception) {
            EdgeChatResult.Err("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
}
