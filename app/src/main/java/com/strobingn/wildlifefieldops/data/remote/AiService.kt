package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.ai.local.LocalLlmModelManager
import com.strobingn.wildlifefieldops.data.model.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
    val severity: String = "MODERATE",
    val notes: String = "",
    val summary: String = ""
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
    private val modelManager: LocalLlmModelManager
) {
    val isConfigured: Boolean get() = BuildConfig.LLM_API_KEY.trim().length >= 10
    val providerLabel: String get() = "LLM"
    val localLlmReady: Boolean get() = localLlm.isReady

    fun configDiagnostics(): String = localLlm.modelStatusLabel()

    suspend fun ask(userMessage: String, species: String = ""): String = withContext(Dispatchers.IO) {
        "AiService stub active — full cloud/local chat pending restore. Local ready: $localLlmReady"
    }

    suspend fun draftEstimateFromJob(
        job: Job,
        drivingMiles: Double? = null,
        taxPercent: Double = 8.125,
        distanceNote: String = ""
    ): EstimateDraft = EstimateDraft(
        fromAi = false,
        mileage = drivingMiles ?: 0.0,
        taxRate = taxPercent,
        rationale = "AiService stub. $distanceNote"
    )

    suspend fun parseJobFromDictation(transcript: String): JobIntakeResult =
        JobIntakeParser.parse(
            transcript = transcript,
            localReady = localLlm.isReady,
            generateLocal = { system, user ->
                runCatching { localLlm.generate(system, user).getOrNull() }.getOrNull()
            },
            cloudConfigured = false,
            completeCloud = { _, _ -> null to "cloud stub" },
            providerLabel = providerLabel,
            localDisplayName = modelManager.activeDisplayName,
            notConfiguredMessage = "On-device/heuristic only (AiService stub)"
        )

    suspend fun writeInspectionReportFromDictation(
        transcript: String,
        context: InspectionReportContext = InspectionReportContext()
    ): InspectionReportResult = InspectionReportResult(
        error = "AiService stub — restore full file for AI Write Report"
    )

    suspend fun summarizeJob(job: Job): String =
        "AiService stub. Job: ${job.title} @ ${job.address}"

    suspend fun askViaSupabase(userMessage: String, species: String = ""): String =
        ask(userMessage, species)

    companion object {
        val CLOUD_SYSTEM_PROMPT: String = "FieldOps AI"
        val WILDLIFE_SYSTEM_PROMPT: String = CLOUD_SYSTEM_PROMPT
        val LOCAL_SYSTEM_PROMPT: String = "On-device assistant"
        fun cloudDiagnosticsOnly(): String = "AiService stub"
    }
}
