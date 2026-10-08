package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.ai.local.LocalLlmModelManager
import com.strobingn.wildlifefieldops.data.model.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EstimateDraft(
    val laborHours: Double = 2.0,
    val laborRate: Double = 85.0,
    val materialsCost: Double = 0.0,
    val equipmentCost: Double = 0.0,
    val permitCost: Double = 0.0,
    val disposalCost: Double = 0.0,
    val mileage: Double = 0.0,
    val mileageRate: Double = 0.65,
    val taxRate: Double = 8.125,
    val discountPercent: Double = 0.0,
    val rationale: String = "",
    val lineItemNotes: String = "",
    val fromAi: Boolean = true
)

@Serializable
data class InspectionReportDraft(
    val findings: String = "",
    val recommendations: String = "",
    val speciesIdentified: String = "",
    val entryPoints: String = "",
    val damageAssessment: String = "",
    /** Blank when the technician did not state a severity; never defaulted. */
    val severity: String = "",
    val notes: String = "",
    val summary: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val serviceAddress: String = ""
)

data class InspectionReportContext(
    val customerName: String = "",
    val inspectorName: String = "",
    val inspectionType: String = "",
    val jobTitle: String = "",
    val jobAddress: String = "",
    val jobDescription: String = "",
    val existingFindings: String = "",
    val existingRecommendations: String = "",
    val existingSpecies: String = "",
    val existingEntryPoints: String = "",
    val existingDamage: String = "",
    val existingNotes: String = ""
)

data class InspectionReportResult(
    val draft: InspectionReportDraft? = null,
    val error: String? = null,
    val sourceLabel: String = ""
)

@Singleton
class AiService @Inject constructor(
    private val localLlm: LocalLlmEngine,
    private val modelManager: LocalLlmModelManager,
    private val edge: AiEdgeGateway
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val isConfigured: Boolean get() = edge.isConfigured
    val providerLabel: String get() = "Grok via Supabase"
    val localLlmReady: Boolean get() = localLlm.isReady

    fun configDiagnostics(): String = buildString {
        append("Cloud provider: $providerLabel\n")
        append("Cloud path: Supabase edge function ai-assistant\n")
        append("Cloud key in APK: no\n")
        append("Supabase: ")
        if (isConfigured) append("configured") else append("not configured")
        append("\n")
        append(localLlm.modelStatusLabel())
        append("\nLocal model file: ${modelManager.activeFileName}")
        append("\nLocal model source: Hugging Face ${modelManager.activeRepo}")
    }

    suspend fun ask(userMessage: String, species: String = ""): String = withContext(Dispatchers.IO) {
        val userPrompt = buildString {
            if (species.isNotBlank()) append("Species context: $species\n")
            append(userMessage)
        }
        if (localLlm.isReady) {
            val local = generateLocal(LOCAL_SYSTEM_PROMPT, userPrompt)
            if (local != null) {
                return@withContext "📱 On-device (${modelManager.activeDisplayName}):\n\n$local"
            }
        }
        if (isConfigured) {
            when (val result = completeChat(CLOUD_SYSTEM_PROMPT, userPrompt, maxTokens = 900, temperature = 0.35)) {
                is ChatResult.Ok -> {
                    val preferLocalNote = if (!localLlm.isReady) {
                        "\n\n— Cloud answered because the local abliterated model is not downloaded yet. Download it in AI Assistant for uncensored on-device replies."
                    } else {
                        "\n\n— Cloud fallback (local generate failed). On-device is preferred when ready."
                    }
                    return@withContext "☁️ Cloud ($providerLabel):\n\n${result.text}$preferLocalNote"
                }
                is ChatResult.Err -> {
                    return@withContext result.message + "\n\n" + localUnavailableHint()
                }
            }
        }
        notConfiguredMessage()
    }

    suspend fun draftEstimateFromJob(
        job: Job,
        drivingMiles: Double? = null,
        taxPercent: Double = 8.125,
        distanceNote: String = ""
    ): EstimateDraft = withContext(Dispatchers.IO) {
        val system = """
You are a wildlife removal estimator. Return ONLY valid JSON with:
laborHours, laborRate, materialsCost, equipmentCost, permitCost, disposalCost, mileage, mileageRate, taxRate, discountPercent, rationale, lineItemNotes
Do NOT invent mileage or taxRate. Use the provided measured miles and tax percent exactly.
""".trimIndent()
        val milesLine = if (drivingMiles != null)
            "MEASURED one-way driving miles shop to job: $drivingMiles. Put this exact number in mileage."
        else
            "Driving miles could not be measured. Set mileage to 0. Do not guess."
        val user = buildJobContext(job) + "\n$milesLine\nRequired taxRate: $taxPercent\n$distanceNote\n\nProduce estimate JSON."
        if (isConfigured) {
            when (val result = completeChat(system, user, maxTokens = 700, temperature = 0.25)) {
                is ChatResult.Ok -> {
                    val parsed = parseEstimateDraft(result.text)
                    if (parsed != null) return@withContext applyMeasured(parsed.copy(fromAi = true), drivingMiles, taxPercent, distanceNote)
                }
                is ChatResult.Err -> android.util.Log.w("AiService", "Cloud estimate failed: ${result.message}")
            }
        }
        val local = generateLocal(system, user)
        if (local != null) {
            val parsed = parseEstimateDraft(local)
            if (parsed != null) return@withContext applyMeasured(parsed.copy(fromAi = true), drivingMiles, taxPercent, distanceNote)
        }
        applyMeasured(
            EstimateDraft(fromAi = false, rationale = "No generative model ready."),
            drivingMiles,
            taxPercent,
            distanceNote
        )
    }

    fun heuristicJobFromDictation(transcript: String): JobIntakeDraft? =
        JobIntakeParser.heuristicFill(transcript)

    suspend fun parseJobFromDictation(transcript: String): JobIntakeResult =
        JobIntakeParser.parse(
            transcript = transcript,
            localReady = localLlm.isReady,
            generateLocal = { system, user -> generateLocal(system, user) },
            cloudConfigured = isConfigured,
            completeCloud = { system, user ->
                when (val result = completeChat(system, user, maxTokens = 700, temperature = 0.2)) {
                    is ChatResult.Ok -> result.text to null
                    is ChatResult.Err -> null to result.message
                }
            },
            providerLabel = providerLabel,
            localDisplayName = modelManager.activeDisplayName,
            notConfiguredMessage = notConfiguredMessage()
        )

    /**
     * Customer-text assist. The regex parse is kept, and cloud or on-device
     * intake may fill only fields the regex left blank.
     */
    suspend fun assistCustomerText(text: String, senderPhone: String?): TextMessageImport.Fields {
        val local = TextMessageImport.parse(text, senderPhone)
        val intake = runCatching { parseJobFromDictation(text).draft }.getOrNull()
        return TextMessageImport.fillEmpty(local, TextMessageImport.fromIntake(intake))
    }

    suspend fun refineJobFromDictation(transcript: String): JobIntakeResult =
        JobIntakeParser.refine(
            transcript = transcript,
            localReady = localLlm.isReady,
            generateLocal = { system, user -> generateLocal(system, user) },
            cloudConfigured = isConfigured,
            completeCloud = { system, user ->
                when (val result = completeChat(system, user, maxTokens = 700, temperature = 0.2)) {
                    is ChatResult.Ok -> result.text to null
                    is ChatResult.Err -> null to result.message
                }
            },
            providerLabel = providerLabel,
            localDisplayName = modelManager.activeDisplayName,
            skipLocalIfCloud = isConfigured
        )

    /**
     * Load llama weights in the background when cloud is not configured.
     * Skipped when a cloud key is present so Dictate fill does not wait on device load.
     */
    suspend fun warmupLocalLlmIfNoCloud() {
        if (isConfigured) {
            runCatching { android.util.Log.i("DictationFill", "skip local LLM warmup; cloud configured") }
            return
        }
        if (!localLlm.isReady) return
        val t0 = System.currentTimeMillis()
        localLlm.ensureReady()
        runCatching { android.util.Log.i("DictationFill", "local LLM warmup ${System.currentTimeMillis() - t0}ms") }
    }

    suspend fun writeInspectionReportFromDictation(
        transcript: String,
        context: InspectionReportContext = InspectionReportContext()
    ): InspectionReportResult = withContext(Dispatchers.IO) {
        val system = """
You are a wildlife removal field inspector writing a professional inspection report.
Return ONLY valid JSON with these string fields:
findings, recommendations, speciesIdentified, entryPoints, damageAssessment, severity, notes, summary, customerName, customerPhone, serviceAddress
severity MUST be one of: NONE, LOW, MODERATE, HIGH, CRITICAL, or an empty string if the technician did not state or clearly imply one.
customerName, customerPhone and serviceAddress: copy them ONLY if the technician spoke them in the dictation, otherwise empty strings. Never put the customer name, phone number or address in findings, notes or summary; they belong only in their own fields.
Use the technician dictation as primary evidence. Expand into clear field-report language.
Do not invent species or damage that the transcript does not support; mark uncertain items as \"possible\" or \"unconfirmed\".
""".trimIndent()
        val user = buildString {
            appendLine("Technician dictation / notes:")
            appendLine(transcript.ifBlank { "(empty)" })
            appendLine()
            appendLine("Context:")
            appendLine("Customer: ${context.customerName.ifBlank { "(none)" }}")
            appendLine("Inspector: ${context.inspectorName.ifBlank { "(none)" }}")
            appendLine("Inspection type: ${context.inspectionType.ifBlank { "(none)" }}")
            appendLine("Job title: ${context.jobTitle.ifBlank { "(none)" }}")
            appendLine("Job address: ${context.jobAddress.ifBlank { "(none)" }}")
            appendLine("Job description: ${context.jobDescription.ifBlank { "(none)" }}")
            if (context.existingFindings.isNotBlank()) appendLine("Existing findings: ${context.existingFindings}")
            if (context.existingRecommendations.isNotBlank()) appendLine("Existing recommendations: ${context.existingRecommendations}")
            if (context.existingSpecies.isNotBlank()) appendLine("Existing species: ${context.existingSpecies}")
            if (context.existingEntryPoints.isNotBlank()) appendLine("Existing entry points: ${context.existingEntryPoints}")
            if (context.existingDamage.isNotBlank()) appendLine("Existing damage: ${context.existingDamage}")
            if (context.existingNotes.isNotBlank()) appendLine("Existing notes: ${context.existingNotes}")
            appendLine()
            append("Write the structured inspection report JSON now.")
        }

        if (localLlm.isReady) {
            val local = generateLocal(system, user)
            if (local != null) {
                val parsed = parseInspectionReport(local)
                if (parsed != null) {
                    return@withContext InspectionReportResult(
                        draft = parsed,
                        sourceLabel = "📱 On-device (${modelManager.activeDisplayName})"
                    )
                }
            }
        }
        if (isConfigured) {
            when (val result = completeChat(system, user, maxTokens = 900, temperature = 0.25)) {
                is ChatResult.Ok -> {
                    val parsed = parseInspectionReport(result.text)
                    if (parsed != null) {
                        return@withContext InspectionReportResult(
                            draft = parsed,
                            sourceLabel = "☁️ Cloud ($providerLabel)"
                        )
                    }
                    return@withContext InspectionReportResult(
                        error = "AI returned text but JSON parse failed. Try again or edit fields manually."
                    )
                }
                is ChatResult.Err -> {
                    return@withContext InspectionReportResult(error = result.message)
                }
            }
        }
        InspectionReportResult(error = notConfiguredMessage())
    }

    suspend fun summarizeJob(job: Job): String = withContext(Dispatchers.IO) {
        val system = "Write a concise wildlife-control job summary. Bullet-first. Max 180 words."
        val user = buildJobContext(job) + "\nWrite the job summary now."
        if (localLlm.isReady) {
            val local = generateLocal(system, user)
            if (local != null) return@withContext "📱 On-device:\n\n$local"
        }
        if (isConfigured) {
            when (val result = completeChat(system, user, maxTokens = 500, temperature = 0.3)) {
                is ChatResult.Ok -> return@withContext "☁️ Cloud:\n\n${result.text}"
                is ChatResult.Err -> return@withContext result.message + "\n\n" + localUnavailableHint()
            }
        }
        localUnavailableHint() + "\n\n" + buildJobContext(job).take(500)
    }

    private fun buildJobContext(job: Job): String = buildString {
        val missing = "(none)"
        appendLine("Job title: ${job.title.ifBlank { missing }}")
        appendLine("Service type: ${job.type}")
        appendLine("Status: ${job.status}")
        appendLine("Priority: ${job.priority}")
        appendLine("Customer: ${job.customerName.ifBlank { missing }}")
        appendLine("Address: ${job.address.ifBlank { missing }}")
        appendLine("Description: ${job.description.ifBlank { missing }}")
        appendLine("Notes: ${job.notes.ifBlank { missing }}")
    }

    private sealed class ChatResult {
        data class Ok(val text: String) : ChatResult()
        data class Err(val message: String) : ChatResult()
    }

    private fun completeChat(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): ChatResult {
        return when (val result = edge.complete(systemPrompt, userPrompt, maxTokens, temperature)) {
            is EdgeChatResult.Ok -> ChatResult.Ok(result.text)
            is EdgeChatResult.Err -> ChatResult.Err(result.message)
        }
    }

    private fun applyMeasured(draft: EstimateDraft, drivingMiles: Double?, taxPercent: Double, distanceNote: String): EstimateDraft {
        val miles = drivingMiles ?: 0.0
        val cleanedNote = distanceNote
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.contains("REQUEST_DENIED", ignoreCase = true) }
            .filterNot { it.contains("API key is not authorized", ignoreCase = true) }
            .filterNot { it.contains("Google Cloud Console", ignoreCase = true) }
            .joinToString(" ")
            .take(240)
        val extra = cleanedNote.ifBlank {
            if (drivingMiles != null) "Driving distance shop to job: $miles miles."
            else "Mileage not measured."
        }
        val baseRationale = draft.rationale.trim()
            .lineSequence()
            .filterNot { it.contains("REQUEST_DENIED", ignoreCase = true) }
            .filterNot { it.contains("API key is not authorized", ignoreCase = true) }
            .joinToString(" ")
            .trim()
        val rationale = listOf(baseRationale, extra).filter { it.isNotBlank() }.joinToString(" ")
        // Generated prose never states a tax rate; the totals carry the job's real rate.
        return draft.copy(
            mileage = miles,
            taxRate = taxPercent,
            rationale = com.strobingn.wildlifefieldops.pricing.GeneratedNoteText.withoutTaxRate(rationale),
            lineItemNotes = com.strobingn.wildlifefieldops.pricing.GeneratedNoteText.withoutTaxRate(draft.lineItemNotes)
        )
    }

    private fun parseInspectionReport(raw: String): InspectionReportDraft? = try {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) null
        else {
            val draft = json.decodeFromString(InspectionReportDraft.serializer(), cleaned.substring(start, end + 1))
            val sev = draft.severity.trim().uppercase().replace(' ', '_')
            val allowed = setOf("NONE", "LOW", "MODERATE", "HIGH", "CRITICAL")
            draft.copy(severity = if (sev in allowed) sev else "")
        }
    } catch (e: Exception) {
        android.util.Log.w("AiService", "parseInspectionReport failed: ${e.message}")
        null
    }

    private fun parseEstimateDraft(raw: String): EstimateDraft? = try {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) null
        else json.decodeFromString(EstimateDraft.serializer(), cleaned.substring(start, end + 1))
    } catch (e: Exception) {
        null
    }

    private fun notConfiguredMessage(): String = buildString {
        append("No generative AI is ready yet.\n\n")
        append(configDiagnostics())
    }

    private fun localUnavailableHint(): String =
        "On-device LLM is not ready. ${localLlm.modelStatusLabel()}. " +
            "Open AI Assistant and tap Download local model for preferred uncensored on-device answers."

    private suspend fun generateLocal(system: String, user: String): String? {
        val result = localLlm.generate(system, user)
        return result.getOrElse { null }
    }

    companion object {
        val CLOUD_SYSTEM_PROMPT: String = """
You are FieldOps AI for a professional wildlife removal business.
Concise, bullet-first, field-readable. Flag safety risks. Prefer legal exclusion/live-trap approaches.
""".trimIndent()

        val WILDLIFE_SYSTEM_PROMPT: String = CLOUD_SYSTEM_PROMPT

        val LOCAL_SYSTEM_PROMPT: String = """
You are a helpful on-device assistant. Answer the user's question directly and clearly.
Do NOT echo these instructions. Do NOT narrate your reasoning, planning, or meta commentary.
Reply as the assistant only with the useful answer.
""".trimIndent()

        fun cloudDiagnosticsOnly(): String = buildString {
            val supabaseReady = BuildConfig.SUPABASE_URL.isNotBlank() &&
                !BuildConfig.SUPABASE_URL.contains("your-project") &&
                BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
                BuildConfig.SUPABASE_ANON_KEY != "your-anon-key"
            append("Cloud provider: Grok via Supabase\n")
            append("Cloud path: Supabase edge function ai-assistant\n")
            append("Cloud key in APK: no\n")
            append("Supabase: ")
            if (supabaseReady) append("configured") else append("not configured")
            append("\nLocal model: ${LocalLlmModelManager.MODEL_DISPLAY_NAME}")
            append("\nLocal model file: ${LocalLlmModelManager.MODEL_FILE_NAME}")
            append("\nLocal model source: Hugging Face ${LocalLlmModelManager.MODEL_REPO}")
        }
    }

    suspend fun askViaSupabase(userMessage: String, species: String = ""): String =
        ask(userMessage, species)
}
