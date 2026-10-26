package com.strobingn.wildlifefieldops.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceDetector
import com.strobingn.wildlifefieldops.ai.fieldops.EvidenceStatus
import com.strobingn.wildlifefieldops.ai.fieldops.FieldSource
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoEvidence
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoEvidenceSessions
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoReviewQueueHub
import com.strobingn.wildlifefieldops.ai.fieldops.VlmRecovery
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.data.remote.AiEdgeGateway
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.EdgeChatResult
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCAL_FORM_TIMEOUT_MS = 45_000L

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
    private val localScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        } catch (t: Throwable) {
            android.util.Log.w("HybridAIService", "vision failed: ${t.message}")
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

        if (vision.evidenceStatus.isNotBlank()) {
            return contractedEvidenceFill(context, vision, prompt)
        }

        if (edge.isConfigured) {
            runCatching {
                val form = callGrokForForm(prompt)
                return enrich(vision, form, source = "grok")
            }.onFailure {
                android.util.Log.w("HybridAIService", "Cloud form fill failed: ${it.message}")
            }
        }

        // 512 tokens (the default) cuts the form JSON mid-object, so ask for more room, and
        // never let the on-device model hold the capture flow for more than the timeout.
        // The native call ignores cancellation, so run it in its own scope and stop waiting.
        val localWork = localScope.async {
            localLlm.generate(AiService.WILDLIFE_SYSTEM_PROMPT, prompt, maxTokens = 900).getOrNull()
        }
        val local = withTimeoutOrNull(LOCAL_FORM_TIMEOUT_MS) { localWork.await() }
        if (local != null) {
            val form = runCatching {
                val cleaned = local.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val start = cleaned.indexOf('{')
                val end = cleaned.lastIndexOf('}')
                require(start >= 0 && end > start) { "no JSON object in local output" }
                gson.fromJson(cleaned.substring(start, end + 1), GrokFormResponse::class.java)
                    ?: error("empty form")
            }.getOrElse {
                // Truncated or malformed JSON must not end up in the notes field as raw text.
                val looksLikeJson = local.contains('{') || local.contains("```")
                GrokFormResponse(
                    species = vision.species.joinToString(", "),
                    serviceType = vision.suggestedServiceType,
                    priority = vision.suggestedPriority,
                    notes = if (looksLikeJson) vision.suggestedNotes else local.take(800),
                    recommendedActions = emptyList()
                )
            }
            return enrich(vision, form, source = "local_llm")
        }

        return vision.copy(
            suggestedNotes = vision.suggestedNotes +
                "\nGenerative LLM unavailable — download the on-device model. Cloud Grok needs Supabase."
        )
        } catch (t: Throwable) {
            android.util.Log.w("HybridAIService", "form fill failed: ${t.message}")
            vision.copy(suggestedNotes = vision.suggestedNotes + "\nAI enrich failed: ${t.message}")
        }
    }

    suspend fun generateTieredEstimate(
        context: Context,
        analysis: AiAnalysisResult,
        jobContext: String = ""
    ): String {
        val prompt = GrokPrompts.tieredEstimatePrompt(analysis, jobContext)
        if (edge.isConfigured) {
            runCatching { return callGrokText(prompt) }
        }
        val local = localLlm.generate(AiService.WILDLIFE_SYSTEM_PROMPT, prompt).getOrNull()
        if (local != null) return "On-device LLM estimate:\n\n$local"
        return "No generative LLM ready. Download the on-device model in AI Assistant. Cloud Grok needs Supabase."
    }

    suspend fun analyzeFormForCompliance(formText: String): List<String> {
        val prompt = GrokPrompts.complianceAuditPrompt(formText)
        if (edge.isConfigured) {
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
        val cloud = if (edge.isConfigured) runCatching { callGrokText(prompt) }.getOrNull() else null
        val raw = cloud ?: localLlm.generate(AiService.WILDLIFE_SYSTEM_PROMPT, prompt).getOrNull()

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

    /**
     * Entry and exterior-damage stills: VLM only when the contract says REVIEW
     * and the device can reach it. Offline reviews stay queued.
     */
    private suspend fun contractedEvidenceFill(
        context: Context,
        vision: AiAnalysisResult,
        prompt: String,
    ): AiAnalysisResult {
        val hash = vision.authoritativeFrameHash
        val screening = PhotoEvidenceSessions.recall(hash)
            ?: return vision.copy(
                suggestedNotes = vision.suggestedNotes +
                    "\nEvidence ${vision.evidenceStatus}. Manual entry available.",
            )
        if (!screening.vlmEligible || screening.status != EvidenceStatus.REVIEW) {
            return vision.copy(
                evidenceStatus = screening.status.name,
                evidenceReasons = screening.reasons.map { it.name },
                vlmEligible = false,
                suggestedNotes = vision.suggestedNotes +
                    "\nEvidence ${screening.status.name}: " +
                    screening.reasons.joinToString { it.name } +
                    ". Manual entry available.",
            )
        }
        val online = edge.isConfigured && networkAvailable(context)
        if (!online) {
            val queued = PhotoReviewQueueHub.shared.enqueue(
                id = hash.ifBlank { "review-${vision.evidenceSummary.hashCode()}" },
                screening = screening,
                nowMs = System.currentTimeMillis(),
            )
            val queueId = queued?.id ?: hash
            return vision.copy(
                evidenceStatus = EvidenceStatus.REVIEW.name,
                evidenceReasons = queued?.screening?.reasons?.map { it.name }
                    ?: screening.reasons.map { it.name },
                vlmEligible = true,
                source = "offline_ml",
                suggestedNotes = vision.suggestedNotes +
                    "\nEvidence REVIEW queued offline ($queueId). Not completed. Manual entry available.",
            )
        }
        val form = runCatching { callGrokForForm(prompt) }.getOrNull()
        if (form == null) {
            val queued = PhotoReviewQueueHub.shared.enqueue(
                id = hash.ifBlank { "review-${vision.evidenceSummary.hashCode()}" },
                screening = screening,
                nowMs = System.currentTimeMillis(),
            )
            return vision.copy(
                evidenceStatus = EvidenceStatus.REVIEW.name,
                evidenceReasons = (screening.reasons.map { it.name } + "OFFLINE_DEFERRED").distinct(),
                vlmEligible = true,
                source = "offline_ml",
                suggestedNotes = vision.suggestedNotes +
                    "\nVLM unavailable; REVIEW stays queued (${queued?.id ?: hash}). Manual entry available.",
            )
        }
        val parsed = WildlifeEvidenceDetector.detectFromText(
            listOf(form.species, form.notes, form.serviceType).joinToString("\n")
        )
        val recovered = buildList {
            if (vision.entryTypes.isEmpty()) {
                parsed.entries.firstOrNull()?.let { hit ->
                    add(VlmRecovery("entry", hit.label, hit.score.coerceIn(0f, 1f)))
                }
            }
            if (vision.damageTypes.isEmpty()) {
                parsed.damage.firstOrNull()?.let { hit ->
                    add(VlmRecovery("damage", hit.label, hit.score.coerceIn(0f, 1f)))
                }
            }
            if (vision.species.isEmpty()) {
                parsed.species.firstOrNull()?.let { hit ->
                    add(VlmRecovery("species", hit.label, hit.score.coerceIn(0f, 1f)))
                }
            }
            if (Regex("""\d+(\.\d+)?\s*(in|inch|ft|feet|cm|mm)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(form.notes)
            ) {
                add(VlmRecovery("extent", "measured", 0.7f))
            }
        }
        val after = PhotoEvidence.applyVlm(
            screening = screening,
            recovered = recovered,
            offline = false,
        )
        if (hash.isNotBlank()) PhotoEvidenceSessions.replace(hash, after)
        if (after.status == EvidenceStatus.ACCEPT) {
            PhotoReviewQueueHub.shared.complete(hash, after)
            val vlmFields = after.evidence.fields.filterValues { it.source == FieldSource.VLM && it.value.isNotBlank() }
            val provenance = if (vlmFields.isEmpty()) {
                ""
            } else {
                "\nVLM recovered " + vlmFields.entries.joinToString { (key, field) ->
                    "$key=${field.value} (${field.source.name} ${"%.2f".format(field.confidence)})"
                } + ". Revalidated."
            }
            return vision.copy(
                species = vision.species.ifEmpty {
                    vlmFields.filterKeys { it == "species" || it.startsWith("species:") }.values.map { it.value }
                },
                damageTypes = vision.damageTypes.ifEmpty {
                    vlmFields.filterKeys { it == "damage" || it.startsWith("damage:") }.values.map { it.value }
                },
                entryTypes = vision.entryTypes.ifEmpty {
                    vlmFields.filterKeys { it == "entry" || it.startsWith("entry:") }.values.map { it.value }
                },
                suggestedServiceType = vision.suggestedServiceType.ifBlank { form.serviceType },
                suggestedPriority = vision.suggestedPriority.ifBlank { form.priority },
                suggestedNotes = vision.suggestedNotes + provenance,
                evidenceStatus = after.status.name,
                evidenceReasons = after.reasons.map { it.name },
                vlmEligible = false,
                source = "grok",
            )
        }
        return vision.copy(
            evidenceStatus = after.status.name,
            evidenceReasons = after.reasons.map { it.name },
            vlmEligible = after.vlmEligible,
            suggestedNotes = vision.suggestedNotes +
                "\nEvidence ${after.status.name}: " +
                after.reasons.joinToString { it.name } +
                ". VLM fields were not kept. Manual entry available.",
        )
    }

    private fun networkAvailable(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) {
        false
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

    private suspend fun callGrokForForm(prompt: String): GrokFormResponse {
        val content = callGrokText(prompt, jsonMode = true)
            .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return gson.fromJson(content, GrokFormResponse::class.java)
    }

    private suspend fun callGrokText(prompt: String, jsonMode: Boolean = false): String {
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
}
