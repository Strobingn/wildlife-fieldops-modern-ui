package com.strobingn.wildlifefieldops.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Request and response contract for the Supabase `ai-assistant` edge function.
 * The xAI key stays on the server. This payload never carries one.
 */
object AiEdgeProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val ACTION_COMPLETE = "complete"

    fun requestJson(
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Double,
        jsonMode: Boolean
    ): String = buildJsonObject {
        put("action", ACTION_COMPLETE)
        put("system", system.take(8_000))
        put("user", user.take(12_000))
        put("maxTokens", maxTokens.coerceIn(1, 2_000))
        put("temperature", temperature.coerceIn(0.0, 1.0))
        put("jsonMode", jsonMode)
    }.toString()

    fun parse(httpCode: Int, body: String): EdgeChatResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val ok = (root?.get("ok") as? JsonPrimitive)?.booleanOrNull
        val text = root?.get("text")?.jsonPrimitive?.contentOrNull?.trim()
        val error = root?.get("error")?.jsonPrimitive?.contentOrNull?.trim()
        if (ok == true && !text.isNullOrEmpty()) {
            val provider = root["provider"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "xai"
            return EdgeChatResult.Ok(text, provider)
        }
        if (!error.isNullOrEmpty()) return EdgeChatResult.Err(error.take(500))
        if (httpCode !in 200..299) return EdgeChatResult.Err("AI error HTTP $httpCode")
        return EdgeChatResult.Err("Empty AI response.")
    }
}

sealed class EdgeChatResult {
    data class Ok(val text: String, val provider: String) : EdgeChatResult()
    data class Err(val message: String) : EdgeChatResult()
}
