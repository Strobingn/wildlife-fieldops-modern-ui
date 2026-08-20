package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ai.field.QuoteBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HybridAIService {
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

    data class FieldToolAnswer(
        val text: String,
        val source: String
    )

    fun hasDirectKey(): Boolean = BuildConfig.LLM_API_KEY.trim().length >= 10

    suspend fun analyzePhotoAndFillForm(
        context: Context,
        imageUri: Uri,
        jobContext: String = ""
    ): AiAnalysisResult {
        val offline = PhotoAIHelper.analyzePhotoForFormFilling(context, imageUri)
        if (!hasDirectKey()) return offline

        return runCatching {
            val prompt = GrokPrompts.photoToFormFill(
                speciesTags = offline.species,
                damageTags = offline.damageTypes,
                location = jobContext
            )
            val form = callGrokForForm(prompt)
            offline.copy(
                species = form.species.split(',').map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { offline.species },
                suggestedServiceType = form.serviceType.ifBlank { offline.suggestedServiceType },
                suggestedPriority = form.priority.ifBlank { offline.suggestedPriority },
                suggestedNotes = buildString {
                    append(form.notes.ifBlank { offline.suggestedNotes })
                    if (form.recommendedActions.isNotEmpty()) {
                        append("\nRecommended actions: ")
                        append(form.recommendedActions.joinToString("; "))
                    }
                    if (form.complianceFlags.isNotEmpty()) {
                        append("\nCompliance flags: ")
                        append(form.complianceFlags.joinToString("; "))
                    }
                },
                estimatedPriceLow = form.estimatedPriceLow.takeIf { it > 0 } ?: offline.estimatedPriceLow,
                estimatedPriceHigh = form.estimatedPriceHigh.takeIf { it > 0 } ?: offline.estimatedPriceHigh,
                estimatedPriceRange = if (form.estimatedPriceLow > 0 && form.estimatedPriceHigh > 0) {
                    "$${String.format("%.0f", form.estimatedPriceLow)} - $${String.format("%.0f", form.estimatedPriceHigh)}"
                } else offline.estimatedPriceRange,
                source = "grok"
            )
        }.getOrElse { offline.copy(suggestedNotes = offline.suggestedNotes + "\nLive Grok enhancement unavailable: ${it.message}") }
    }

    suspend fun generateTieredEstimate(
        context: Context,
        analysis: AiAnalysisResult,
        jobContext: String = ""
    ): String {
        val quote = QuoteBuilder.fromNotes(
            notes = analysis.suggestedNotes,
            speciesHint = analysis.species.joinToString(),
            snapshot = jobContext
        )
        return quote.asText()
    }

    suspend fun analyzeFormForCompliance(formText: String): List<String> {
        return QuoteBuilder.fromNotes(formText).flags.ifEmpty {
            listOf("No season hold from the text. Still verify DEC / maternity on site.")
        }
    }

    suspend fun estimateFromWalkthrough(
        spokenNotes: String,
        photoSummaries: List<String>
    ): FieldToolAnswer {
        val quote = QuoteBuilder.fromNotes(
            notes = spokenNotes,
            snapshot = photoSummaries.joinToString("\n")
        )
        return FieldToolAnswer(quote.asText(), "Field engine")
    }

    suspend fun answerFieldTool(
        toolTitle: String,
        purpose: String,
        steps: List<String>,
        species: String,
        siteNotes: String,
        jobSnapshot: String = ""
    ): FieldToolAnswer = withContext(Dispatchers.IO) {
        val quote = QuoteBuilder.fromNotes(
            notes = siteNotes.ifBlank { "$toolTitle. $purpose. ${steps.joinToString()}" },
            speciesHint = species,
            snapshot = jobSnapshot
        )
        FieldToolAnswer(quote.asText(), "Field engine")
    }

    private suspend fun callGrokForForm(prompt: String): GrokFormResponse {
        val content = callGrokText(prompt, jsonMode = true)
            .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return gson.fromJson(content, GrokFormResponse::class.java)
    }

    private suspend fun callGrokText(
        prompt: String,
        jsonMode: Boolean = false,
        system: String = GrokPrompts.SYSTEM
    ): String = withContext(Dispatchers.IO) {
        val body = mutableMapOf<String, Any>(
            "model" to BuildConfig.LLM_MODEL,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
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
