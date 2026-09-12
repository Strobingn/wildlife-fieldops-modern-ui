package com.strobingn.wildlifefieldops.ai

object GrokPrompts {
    const val SYSTEM = "You are an expert wildlife removal technician and field ops AI. Always return valid JSON only. Be precise, use realistic pricing for New York / New Jersey wildlife jobs in 2026. Prioritize safety, compliance (rabies, permits), insurance documentation, and customer satisfaction."

    fun photoToFormFill(
        speciesTags: List<String>,
        damageTags: List<String>,
        location: String? = null,
        recentJobs: String = "",
        voiceTranscript: String = "",
        evidenceSummary: String = "",
        entryTags: List<String> = emptyList(),
        arMeasurement: String = ""
    ): String = """
Analyze wildlife job photo evidence.
Species tags: ${speciesTags.joinToString()}
Damage tags: ${damageTags.joinToString()}
Entry tags: ${entryTags.joinToString()}
Evidence summary: $evidenceSummary
AR / span measurement: $arMeasurement
Voice dictation during capture: $voiceTranscript
Location / job context: $location
Recent jobs: $recentJobs
Merge vision + voice. Prefer FieldOps species/entry vocabulary.
Return strict JSON with species, serviceType, priority, notes, recommendedActions, estimatedPriceLow, estimatedPriceHigh, complianceFlags.
"""

    fun tieredEstimatePrompt(analysis: AiAnalysisResult, jobContext: String = ""): String = """
Create a professional wildlife removal estimate for ${analysis.suggestedServiceType}.
Species: ${analysis.species.joinToString()}
Notes: ${analysis.suggestedNotes}
Context: $jobContext
Return Good/Better/Best options with realistic pricing.
"""

    fun complianceAuditPrompt(formText: String): String = """
Audit this wildlife removal job form for compliance issues: $formText
Return a bullet list of issues and recommendations.
"""

    fun complianceAudit(serviceType: String, formText: String): String = complianceAuditPrompt("$serviceType $formText")

    fun voiceToStructuredJob(voiceText: String, currentJobContext: String = ""): String =
        "Convert voice note to structured wildlife job JSON: $voiceText $currentJobContext"

    fun predictTrapCheckPriority(trapHistory: String, species: String, weather: String, season: String): String =
        "Predict trap check priority for $species based on $weather $season $trapHistory"

    fun arMeasurementToReport(measurements: String, species: String, damageType: String): String =
        "Create report from $measurements for $species $damageType"

    fun liveCaptureNarration(
        checklistTitle: String?,
        species: List<String>,
        damage: List<String>,
        serviceType: String,
        visionNotes: String,
        reasonCode: String,
        jobContext: String = "",
        voiceTranscript: String = "",
        evidenceSummary: String = "",
        arMeasurement: String = ""
    ): String = """
You are writing field notes after a policy-accepted Live Capture still.
Checklist item: ${checklistTitle ?: "general evidence"}
Species tags: ${species.joinToString()}
Damage tags: ${damage.joinToString()}
Evidence summary: $evidenceSummary
Service suggestion: $serviceType
Vision notes: $visionNotes
AR / span measurement: $arMeasurement
Voice dictation (merge with vision; resolve conflicts conservatively): $voiceTranscript
Policy reason: $reasonCode
Job context: $jobContext

Return plain text with exactly two sections and these headers:
TECH_NOTES:
(3-6 concise technician bullets: evidence, access, hazards, measurements, next field action; weave in voice cues)

CUSTOMER_SUMMARY:
(2-4 short customer-safe sentences; no internal pricing strategy; calm and professional)
"""

    fun mergeVoiceVisionReport(
        voiceTranscript: String,
        evidenceSummary: String,
        techNotes: String,
        customerSummary: String,
        jobContext: String = ""
    ): String = """
Merge Live Capture voice + vision into one inspection narrative.
Voice: $voiceTranscript
Evidence: $evidenceSummary
Tech notes: $techNotes
Customer summary: $customerSummary
Job: $jobContext
Return plain text with headers TECH_NOTES: and CUSTOMER_SUMMARY: only.
"""
}
