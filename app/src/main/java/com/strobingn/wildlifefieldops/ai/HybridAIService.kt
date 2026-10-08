package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.strobingn.wildlifefieldops.ai.local.HardTimeout
import com.strobingn.wildlifefieldops.ai.local.LlmJsonSalvage
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.data.remote.AiEdgeGateway
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.EdgeChatResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid photo → form fill: ML Kit vision labels + generative LLM
 * (cloud Grok when configured, otherwise on-device abliterated llama.cpp GGUF).
 */
@Singleton
class HybridAIService @Inject constructor(
    private val localLlm: LocalLlmEngine,
    private val edge: AiEdgeGateway
) {
    private val gson = Gson()

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

    data class CaptureNarration(
        val techNotes: String,
        val customerSummary: String,
        val source: String
    )

    suspend fun analyzePhotoAndFillForm(
        context: Context,
        imageUri: Uri,
        jobContext: String = "",
        voiceTranscript: String = "",
        evidenceSummary: String = "",
        entryTags: List<String> = emptyList(),
        arMeasurement: String = "",
        equipmentTags: List<String> = emptyList(),
        repairScope: String = ""
    ): AiAnalysisResult {
        val vision = try {
            PhotoAIHelper.analyzePhotoForFormFilling(context, imageUri)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "vision failed: ${t.message}")
            AiAnalysisResult(suggestedNotes = "Vision unavailable: ${t.message}. Manual entry required.")
        }
        return try {
            val prompt = GrokPrompts.photoToFormFill(
                speciesTags = vision.species,
                damageTags = vision.damageTypes,
                location = jobContext,
                voiceTranscript = voiceTranscript,
                evidenceSummary = evidenceSummary.ifBlank { vision.suggestedNotes },
                entryTags = entryTags,
                arMeasurement = arMeasurement,
                equipmentTags = equipmentTags,
                repairScope = repairScope
            )

            // Cloud first (bounded by CLOUD_TIMEOUT_MS and skipped for a while after a failure so a
            // dead connection does not stall every capture), then the on-device model.
            val cloud = cloudComplete(prompt, jsonMode = true)
            val cloudText = cloud.getOrNull()
            if (cloudText != null) {
                val cloudForm = parseFormJson(cloudText)
                if (cloudForm != null) return enrich(vision, cloudForm, source = "grok")
                android.util.Log.w(TAG, "Cloud form fill returned unparseable JSON; trying on-device model")
            }

            val localResult = localGenerate(
                prompt + LOCAL_JSON_SUFFIX,
                LOCAL_FORM_MAX_TOKENS,
                LOCAL_FORM_TIMEOUT_MS
            )
            val local = localResult.getOrNull()
            if (local != null) {
                val form = parseFormJson(local) ?: GrokFormResponse(
                    species = vision.species.joinToString(", "),
                    serviceType = vision.suggestedServiceType,
                    priority = vision.suggestedPriority,
                    // Never dump raw (possibly truncated) JSON into the notes field.
                    notes = if (LlmJsonSalvage.looksLikeJson(local)) vision.suggestedNotes else local.trim().take(800),
                    recommendedActions = emptyList()
                )
                return enrich(vision, form, source = "local_llm")
            }

            val reasons = listOfNotNull(
                cloud.exceptionOrNull()?.message?.takeIf { edge.isConfigured },
                localResult.exceptionOrNull()?.message
            )
            vision.copy(
                suggestedNotes = vision.suggestedNotes + "\nAI enrichment unavailable: " +
                    reasons.joinToString("; ").ifBlank {
                        "download the on-device model; cloud Grok needs Supabase."
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "form fill failed: ${t.message}")
            vision.copy(suggestedNotes = vision.suggestedNotes + "\nAI enrich failed: ${t.message}")
        }
    }

    suspend fun generateTieredEstimate(
        context: Context,
        analysis: AiAnalysisResult,
        jobContext: String = ""
    ): String {
        val prompt = GrokPrompts.tieredEstimatePrompt(analysis, jobContext)
        val cloud = cloudComplete(prompt, jsonMode = false).getOrNull()
        if (cloud != null) return cloud
        val local = localGenerate(prompt, LOCAL_NARRATION_MAX_TOKENS, LOCAL_NARRATION_TIMEOUT_MS).getOrNull()
        if (local != null) return "On-device LLM estimate:\n\n$local"
        return "No generative LLM ready. Download the on-device model in AI Assistant. Cloud Grok needs Supabase."
    }

    suspend fun analyzeFormForCompliance(formText: String): List<String> {
        val prompt = GrokPrompts.complianceAuditPrompt(formText)
        val text = cloudComplete(prompt, jsonMode = false).getOrNull()
            ?: localGenerate(prompt, LOCAL_NARRATION_MAX_TOKENS, LOCAL_NARRATION_TIMEOUT_MS).getOrNull()
        if (text != null) {
            return text.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }
                .filter { it.isNotBlank() }
        }
        return listOf("No generative LLM ready for compliance analysis.")
    }

    /**
     * Phase 3: after policy ACCEPT + vision/form draft, LLM writes tech notes + customer summary.
     * Does not decide capture acceptance.
     */
    suspend fun narrateAcceptedCapture(
        analysis: AiAnalysisResult,
        checklistTitle: String? = null,
        reasonCode: String = "QUALITY_OK",
        jobContext: String = "",
        voiceTranscript: String = "",
        evidenceSummary: String = "",
        arMeasurement: String = "",
        equipmentTags: List<String> = emptyList(),
        repairScope: String = ""
    ): CaptureNarration {
        val prompt = GrokPrompts.liveCaptureNarration(
            checklistTitle = checklistTitle,
            species = analysis.species,
            damage = analysis.damageTypes,
            serviceType = analysis.suggestedServiceType,
            visionNotes = analysis.suggestedNotes,
            reasonCode = reasonCode,
            jobContext = jobContext,
            voiceTranscript = voiceTranscript,
            evidenceSummary = evidenceSummary,
            arMeasurement = arMeasurement,
            equipmentTags = equipmentTags,
            repairScope = repairScope
        )
        val cloud = cloudComplete(prompt, jsonMode = false).getOrNull()?.takeIf { it.isNotBlank() }
        val raw = cloud ?: localGenerate(
            prompt,
            LOCAL_NARRATION_MAX_TOKENS,
            LOCAL_NARRATION_TIMEOUT_MS
        ).getOrNull()

        if (raw.isNullOrBlank()) {
            return CaptureNarration(
                techNotes = buildString {
                    append("• Evidence captured for ")
                    append(checklistTitle ?: "inspection")
                    append("\n• Review on site; confirm species and entry points.")
                    append("\n• Download the on-device model for fuller AI notes.")
                },
                customerSummary = "We documented the area during inspection and will review findings with you.",
                source = "template"
            )
        }
        return parseNarration(raw, if (cloud != null) "grok" else "local_llm")
    }

    private fun parseNarration(raw: String, source: String): CaptureNarration {
        val text = raw.trim()
        val techMarker = Regex("(?i)TECH_NOTES\\s*:")
        val custMarker = Regex("(?i)CUSTOMER_SUMMARY\\s*:")
        val techIdx = techMarker.find(text)?.range?.last?.plus(1) ?: -1
        val custMatch = custMarker.find(text)
        val custIdx = custMatch?.range?.last?.plus(1) ?: -1
        val tech = when {
            techIdx >= 0 && custMatch != null -> text.substring(techIdx, custMatch.range.first).trim()
            techIdx >= 0 -> text.substring(techIdx).trim()
            else -> text.take(600)
        }
        val cust = when {
            custIdx >= 0 -> text.substring(custIdx).trim()
            else -> "We documented conditions during the inspection and will follow up with recommendations."
        }
        return CaptureNarration(techNotes = tech, customerSummary = cust, source = source)
    }

    private fun enrich(vision: AiAnalysisResult, form: GrokFormResponse, source: String): AiAnalysisResult {
        return vision.copy(
            species = form.species.split(',').map { it.trim() }.filter { it.isNotBlank() }
                .ifEmpty { vision.species },
            // Keep on-device scores; cloud prose must not become an operational ID.
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

    /**
     * Strict Gson parse first; if the output was cut off by the token limit (or has stray prose),
     * recover whatever complete fields exist. Returns null when nothing usable was found.
     */
    private fun parseFormJson(raw: String): GrokFormResponse? {
        val json = LlmJsonSalvage.extractObjectText(raw)
        val strict: GrokFormResponse? = try {
            gson.fromJson(json, GrokFormResponse::class.java)
        } catch (_: Throwable) {
            null
        }
        if (strict != null) {
            // Gson can leave non-null Kotlin fields null when the JSON has explicit nulls.
            val species: String? = strict.species
            val serviceType: String? = strict.serviceType
            val priority: String? = strict.priority
            val notes: String? = strict.notes
            val actions: List<String>? = strict.recommendedActions
            val flags: List<String>? = strict.complianceFlags
            return GrokFormResponse(
                species = species.orEmpty(),
                serviceType = serviceType.orEmpty(),
                priority = priority.orEmpty().ifBlank { "MEDIUM" },
                notes = notes.orEmpty(),
                recommendedActions = actions.orEmpty(),
                estimatedPriceLow = strict.estimatedPriceLow,
                estimatedPriceHigh = strict.estimatedPriceHigh,
                complianceFlags = flags.orEmpty()
            )
        }
        val notes = LlmJsonSalvage.extractString(json, "notes")
        val species = LlmJsonSalvage.extractString(json, "species")
        val service = LlmJsonSalvage.extractString(json, "serviceType")
        if (notes == null && species == null && service == null) return null
        return GrokFormResponse(
            species = species.orEmpty(),
            serviceType = service.orEmpty(),
            priority = LlmJsonSalvage.extractString(json, "priority") ?: "MEDIUM",
            notes = notes.orEmpty(),
            recommendedActions = LlmJsonSalvage.extractStringList(json, "recommendedActions"),
            estimatedPriceLow = LlmJsonSalvage.extractNumber(json, "estimatedPriceLow") ?: 0.0,
            estimatedPriceHigh = LlmJsonSalvage.extractNumber(json, "estimatedPriceHigh") ?: 0.0,
            complianceFlags = LlmJsonSalvage.extractStringList(json, "complianceFlags")
        )
    }

    @Volatile private var cloudBackoffUntilMs = 0L

    /** Cloud call with a hard time limit; after a failure/timeout cloud is skipped for [CLOUD_BACKOFF_MS]. */
    private suspend fun cloudComplete(prompt: String, jsonMode: Boolean): Result<String> {
        if (!edge.isConfigured) {
            return Result.failure<String>(IllegalStateException("Cloud AI is not configured"))
        }
        if (System.currentTimeMillis() < cloudBackoffUntilMs) {
            return Result.failure<String>(IllegalStateException("Cloud AI skipped after a recent failure"))
        }
        return try {
            val text = HardTimeout.run(CLOUD_TIMEOUT_MS) { callGrokText(prompt, jsonMode) }
            if (text == null) {
                cloudBackoffUntilMs = System.currentTimeMillis() + CLOUD_BACKOFF_MS
                Result.failure<String>(IllegalStateException("Cloud AI timed out after ${CLOUD_TIMEOUT_MS / 1000}s"))
            } else {
                Result.success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Cloud AI failed: ${t.message}")
            cloudBackoffUntilMs = System.currentTimeMillis() + CLOUD_BACKOFF_MS
            Result.failure<String>(t)
        }
    }

    /** On-device generation with a hard time limit (llama.cpp cannot be interrupted once started). */
    private suspend fun localGenerate(prompt: String, maxTokens: Int, timeoutMs: Long): Result<String> {
        return try {
            val result = HardTimeout.run(timeoutMs) {
                localLlm.generate(AiService.WILDLIFE_SYSTEM_PROMPT, prompt, maxTokens)
            }
            result ?: Result.failure<String>(
                IllegalStateException("On-device AI timed out after ${timeoutMs / 1000}s")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure<String>(t)
        }
    }

    // Blocking HTTP call: only invoked from HardTimeout (Dispatchers.IO), never on the caller's thread.
    private fun callGrokText(prompt: String, jsonMode: Boolean = false): String {
        return when (val result = edge.complete(
            system = GrokPrompts.SYSTEM,
            user = prompt,
            maxTokens = 900,
            temperature = 0.2,
            jsonMode = jsonMode
        )) {
            is EdgeChatResult.Ok -> result.text
            is EdgeChatResult.Err -> error(result.message)
        }
    }

    private companion object {
        const val TAG = "HybridAIService"
        const val CLOUD_TIMEOUT_MS = 25_000L
        const val CLOUD_BACKOFF_MS = 120_000L
        const val LOCAL_FORM_TIMEOUT_MS = 120_000L
        const val LOCAL_NARRATION_TIMEOUT_MS = 90_000L

        /** The default 512 cut the form JSON mid-string; the suffix below also keeps the output short. */
        const val LOCAL_FORM_MAX_TOKENS = 700
        const val LOCAL_NARRATION_MAX_TOKENS = 512
        const val LOCAL_JSON_SUFFIX =
            "\nRespond with ONLY the JSON object, no markdown. Keep notes under 60 words and " +
                "recommendedActions to at most 4 short items."
    }
}
