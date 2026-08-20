package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.WildlifeFieldOpsApp
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
        if (!hasDirectKey()) {
            return "Offline estimate\nGood: $${analysis.estimatedPriceLow}\nBetter: $${analysis.estimatedPriceHigh}\nBest: $${analysis.estimatedPriceHigh + 200}"
        }
        return runCatching {
            callGrokText(GrokPrompts.tieredEstimatePrompt(analysis, jobContext))
        }.getOrElse {
            "Live estimate unavailable. Offline range: $${analysis.estimatedPriceLow} - $${analysis.estimatedPriceHigh}"
        }
    }

    suspend fun analyzeFormForCompliance(formText: String): List<String> {
        if (!hasDirectKey()) return listOf("Offline mode: manually verify state rules, permits, protected species, and pesticide labels.")
        return runCatching {
            val text = callGrokText(GrokPrompts.complianceAuditPrompt(formText))
            text.lines().map { it.trim().removePrefix("-").removePrefix("\u2022").trim() }.filter { it.isNotBlank() }
        }.getOrElse { listOf("Compliance analysis failed: ${it.message}") }
    }

    suspend fun estimateFromWalkthrough(
        spokenNotes: String,
        photoSummaries: List<String>
    ): FieldToolAnswer = answerFieldTool(
        toolTitle = "Walk-and-talk inspection estimate",
        purpose = "Turn spoken field notes and photo tags into a Good/Better/Best quote the tech can read to the customer on site.",
        steps = listOf(
            "Use only what the tech said and what the photos tagged",
            "Hudson Valley wildlife pricing, 2026",
            "Good / Better / Best with labor, materials, follow-ups",
            "Flag missing measurements instead of inventing openings or animals"
        ),
        species = "",
        siteNotes = spokenNotes.ifBlank { "No spoken notes yet." },
        jobSnapshot = photoSummaries.joinToString("\n").ifBlank { "No photos tagged yet." }
    )

    suspend fun answerFieldTool(
        toolTitle: String,
        purpose: String,
        steps: List<String>,
        species: String,
        siteNotes: String,
        jobSnapshot: String = ""
    ): FieldToolAnswer = withContext(Dispatchers.IO) {
        val userPrompt = buildString {
            appendLine("Wildlife FieldOps tool: $toolTitle")
            appendLine("Purpose: $purpose")
            appendLine("SOP constraints only. Do not repeat the SOP as the answer.")
            steps.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
            appendLine()
            if (jobSnapshot.isNotBlank()) {
                appendLine("Job-record snapshot (spreadsheet math, not a model):")
                appendLine(jobSnapshot.trim())
                appendLine()
            }
            appendLine("Tech notes:")
            appendLine("Species: ${species.ifBlank { "not specified" }}")
            appendLine(siteNotes.ifBlank { "none given — ask for the missing facts instead of inventing them" })
            appendLine()
            appendLine("Write a job-specific answer. Numbers, next actions, and what to measure if facts are missing.")
            appendLine("Hudson Valley / NY wildlife control. No legal advice. No generic brochure copy.")
        }

        val app = WildlifeFieldOpsApp.instanceOrNull()
        if (app != null) {
            if (!OnDeviceLlm.isReady(app) && OnDeviceLlm.hasHfToken()) {
                OnDeviceLlm.download(app)
            }
            if (OnDeviceLlm.isReady(app)) {
                val phone = runCatching { OnDeviceLlm.generate(app, userPrompt) }.getOrNull()
                if (!phone.isNullOrBlank()) {
                    return@withContext FieldToolAnswer(phone.trim(), "Phone")
                }
            }
        }

        val maf = runCatching {
            AgentFrameworkClient.runOrNull(
                userMessage = userPrompt,
                species = species,
                agent = "orchestrator",
                context = mapOf(
                    "tool" to toolTitle,
                    "notes" to siteNotes,
                    "snapshot" to jobSnapshot.take(1200)
                )
            )
        }.getOrNull()
        if (!maf.isNullOrBlank()) {
            return@withContext FieldToolAnswer(maf.trim(), "MAF")
        }

        if (hasDirectKey()) {
            val grok = runCatching {
                callGrokText(
                    prompt = userPrompt,
                    jsonMode = false,
                    system = FIELD_SYSTEM
                )
            }.getOrNull()
            if (!grok.isNullOrBlank()) {
                return@withContext FieldToolAnswer(grok.trim(), "Grok")
            }
        }

        FieldToolAnswer(
            text = buildString {
                appendLine("Not AI. No phone model, Grok, or MAF answer.")
                appendLine()
                when {
                    app != null && OnDeviceLlm.progress.value.error.isNotBlank() ->
                        appendLine(OnDeviceLlm.progress.value.error)
                    !OnDeviceLlm.hasHfToken() ->
                        appendLine("Phone model needs repo secret HF_TOKEN (Hugging Face token with Gemma license accepted), then rebuild and tap Run live AI on Wi-Fi to download Gemma 3 1B (~555MB) onto this phone.")
                    else ->
                        appendLine("Phone model not installed or failed to load.")
                }
                if (!hasDirectKey()) {
                    appendLine("Cloud Grok also has no XAI_API_KEY in this APK.")
                }
            }.trim(),
            source = "Offline"
        )
    }

    private const val FIELD_SYSTEM =
        "You are a Hudson Valley wildlife exclusion technician. Answer in plain field notes, not JSON. " +
            "Use the tech's measurements and job records. Do not invent openings, prices, or animals. " +
            "Prefer 16-ga hardware cloth, mechanical fasteners, and NY/DEC-safe timing. " +
            "Never claim a checklist is AI."

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
