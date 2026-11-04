package com.strobingn.wildlifefieldops.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
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
        jsonMode: Boolean,
        imageDataUrl: String? = null,
        detectionExamples: List<DetectionExampleWire> = emptyList(),
    ): String = buildJsonObject {
        put("action", ACTION_COMPLETE)
        put("system", system.take(8_000))
        put("user", user.take(12_000))
        put("maxTokens", maxTokens.coerceIn(1, 2_000))
        put("temperature", temperature.coerceIn(0.0, 1.0))
        put("jsonMode", jsonMode)
        val image = imageDataUrl?.trim().orEmpty()
        if (image.startsWith("data:image/") && image.length <= MAX_IMAGE_CHARS) {
            put("imageDataUrl", image)
        }
        // Omitted when empty so older edge functions see the same complete payload.
        val examples = detectionExamples.take(6).filter {
            it.label.isNotBlank() || it.description.isNotBlank()
        }
        if (examples.isNotEmpty()) {
            put("detectionExamples", buildJsonArray {
                examples.forEach { example ->
                    add(buildJsonObject {
                        put("label", example.label.take(80))
                        put("description", example.description.take(240))
                        put("verdict", example.verdict.take(20))
                        put("modelLabel", example.modelLabel.take(80))
                        val thumb = example.thumbnailDataUrl.trim()
                        if (thumb.startsWith("data:image/") && thumb.length <= 40_000) {
                            put("thumbnailDataUrl", thumb)
                        }
                    })
                }
            })
        }
    }.toString()

    const val MAX_IMAGE_CHARS = 1_500_000

    fun parse(httpCode: Int, body: String): EdgeChatResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val ok = (root?.get("ok") as? JsonPrimitive)?.booleanOrNull
        val text = root?.get("text")?.jsonPrimitive?.contentOrNull?.trim()
        val error = root?.get("error")?.jsonPrimitive?.contentOrNull?.trim()
        if (ok == true && !text.isNullOrEmpty()) {
            val provider = root["provider"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "xai"
            val vision = (root["vision"] as? JsonPrimitive)?.booleanOrNull == true
            return EdgeChatResult.Ok(text, provider, vision, referenceMatch(root))
        }
        if (!error.isNullOrEmpty()) return EdgeChatResult.Err(error.take(500))
        if (httpCode !in 200..299) return EdgeChatResult.Err("AI error HTTP $httpCode")
        return EdgeChatResult.Err("Empty AI response.")
    }

    private fun referenceMatch(root: kotlinx.serialization.json.JsonObject): ReferenceLibraryMatch? {
        val match = root["referenceMatch"] ?: return null
        val objectMatch = runCatching { match.jsonObject }.getOrNull() ?: return null
        val species = objectMatch["species"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val count = objectMatch["similarPhotos"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        if (species.isEmpty() || count <= 0) return null
        return ReferenceLibraryMatch(species, count)
    }
}

data class ReferenceLibraryMatch(
    val species: String,
    val similarPhotos: Int,
)

data class DetectionExampleWire(
    val label: String,
    val description: String,
    val verdict: String = "",
    val modelLabel: String = "",
    val thumbnailDataUrl: String = "",
)

sealed class EdgeChatResult {
    data class Ok(
        val text: String,
        val provider: String,
        val vision: Boolean = false,
        val referenceMatch: ReferenceLibraryMatch? = null,
    ) : EdgeChatResult()
    data class Err(val message: String) : EdgeChatResult()
}
