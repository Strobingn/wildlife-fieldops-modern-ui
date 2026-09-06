package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.data.remote.AiService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid photo → form fill: ML Kit vision labels + generative LLM
 * (cloud Grok when configured, otherwise on-device abliterated llama.cpp GGUF).
 */
@Singleton
class HybridAIService @Inject constructor(
    private val localLlm: LocalLlmEngine
) {
    private val client = HttpClient()
    private val gson = Gson()

    private data class ChatEnvelope(val choices: List<Choice> = emptyList())
    private data class Choice(val message: Message = Message())
    private data class Message(val content: String = "")

    data class GrokFormResponse(
        val species: String = "",
        val serviceType: String = "",
        val priority: String = "MEDIUM",
        val notes: String = "",
        val recommendedActions: List<String> = emptyList(),
        val estimatedPriceLow: Double = 0.0,
        val estimatedPriceHigh: Double = 0.0,
        val complianceFlags: List<String> = emptyList()
    )

    suspend fun analyzePhotoAndFillForm(
        context: Context,
        imageUri: Uri,
        jobContext: String = ""
    ): AiAnalysisResult {
        val vision = PhotoAIHelper.analyzePhotoForFormFilling(context, imageUri)
        val prompt = GrokPrompts.photoToFormFill(
            speciesTags = vision.species,
            damageTags = vision.damageTypes,
            location = jobContext
        )

        if (hasDirectKey()) {
            runCatching {
                val form = callGrokForForm(prompt)
                return enrich(vision, form, source = "grok")
            }.onFailure {
                android.util.Log.w("HybridAIService", "Cloud form fill failed: ${it.message}")
            }
        }

        val local = localLlm.generate(AiService.WILDLIFE_SYSTEM_PROMPT, prompt).getOrNull()
        if (local != null) {
            val form = runCatching {
                val cleaned = local.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                gson.fromJson(cleaned, GrokFormResponse::class.java)
            }.getOrElse {
                GrokFormResponse(
                    species = vision.species.joinToString(", "),
                    serviceType = vision.suggestedServiceType,
                    priority = vision.suggestedPriority,
                    notes = local.take(800),
                    recommendedActions = emptyList()
                )
            }
            return enrich(vision, form, source = "local_llm")
        }

        return vision.copy(
            suggestedNotes = vision.suggestedNotes +
                "\nGenerative LLM unavailable — download on-device model or configure XAI_API_KEY."
        )
    }

    suspend fun generateTieredEstimate(
        context: Context,
        analysis: AiAnalysisResult,
        jobContext: String = ""
    ): String {
        val prompt = GrokPrompts.tieredEstimatePrompt(analysis, jobContext)
        if (hasDirectKey()) {
            runCatching { return callGrokText(prompt) }
        }
        val local = localLlm.generate(AiService.WILDLIFE_SYSTEM_PROMPT, prompt).getOrNull()
        if (local != null) return "📱 On-device LLM estimate:\n\n$local"
        return "No generative LLM ready. Download the on-device model in AI Assistant or set XAI_API_KEY."
    }

    suspend fun analyzeFormForCompliance(formText: String): List<String> {
        val prompt = GrokPrompts.complianceAuditPrompt(formText)
        if (hasDirectKey()) {
            runCatching {
                val text = callGrokText(prompt)
                return text.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }
                    .filter { it.isNotBlank() }
            }
        }
        val local = localLlm.generate(AiService.WILDLIFE_SYSTEM_PROMPT, prompt).getOrNull()
        if (local != null) {
            return local.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }
                .filter { it.isNotBlank() }
        }
        return listOf("No generative LLM ready for compliance analysis.")
    }

    private fun enrich(vision: AiAnalysisResult, form: GrokFormResponse, source: String): AiAnalysisResult {
        return vision.copy(
            species = form.species.split(',').map { it.trim() }.filter { it.isNotBlank() }
                .ifEmpty { vision.species },
            suggestedServiceType = form.serviceType.ifBlank { vision.suggestedServiceType },
            suggestedPriority = form.priority.ifBlank { vision.suggestedPriority },
            suggestedNotes = buildString {
                append(form.notes.ifBlank { vision.suggestedNotes })
                if (form.recommendedActions.isNotEmpty()) {
                    append("\nRecommended actions: ")
                    append(form.recommendedActions.joinToString("; "))
                }
                if (form.complianceFlags.isNotEmpty()) {
                    append("\nCompliance flags: ")
                    append(form.complianceFlags.joinToString("; "))
                }
            },
            estimatedPriceLow = form.estimatedPriceLow.takeIf { it > 0 } ?: vision.estimatedPriceLow,
            estimatedPriceHigh = form.estimatedPriceHigh.takeIf { it > 0 } ?: vision.estimatedPriceHigh,
            estimatedPriceRange = if (form.estimatedPriceLow > 0 && form.estimatedPriceHigh > 0) {
                "$${String.format("%.0f", form.estimatedPriceLow)} - $${String.format("%.0f", form.estimatedPriceHigh)}"
            } else vision.estimatedPriceRange,
            source = source
        )
    }

    private fun hasDirectKey(): Boolean = BuildConfig.LLM_API_KEY.trim().length >= 10

    private suspend fun callGrokForForm(prompt: String): GrokFormResponse {
        val content = callGrokText(prompt, jsonMode = true)
            .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return gson.fromJson(content, GrokFormResponse::class.java)
    }

    private suspend fun callGrokText(prompt: String, jsonMode: Boolean = false): String = withContext(Dispatchers.IO) {
        val body = mutableMapOf<String, Any>(
            "model" to BuildConfig.LLM_MODEL,
            "messages" to listOf(
                mapOf("role" to "system", "content" to GrokPrompts.SYSTEM),
                mapOf("role" to "user", "content" to prompt)
            ),
            "temperature" to 0.2,
            "max_tokens" to 900
        )
        if (jsonMode) body["response_format"] = mapOf("type" to "json_object")

        val response: String = client.post("${BuildConfig.LLM_BASE_URL.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            headers { append("Authorization", "Bearer ${BuildConfig.LLM_API_KEY.trim()}") }
            setBody(gson.toJson(body))
        }.body()

        val envelope = gson.fromJson(response, ChatEnvelope::class.java)
        envelope.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
            ?: error("Empty Grok response")
    }
}
