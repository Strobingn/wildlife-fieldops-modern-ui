package com.strobingn.wildlifefieldops.ai

object GrokPrompts {
    const val SYSTEM = "You are an expert wildlife removal technician and field ops AI. Always return valid JSON only. Be precise, use realistic pricing for New York / New Jersey wildlife jobs in 2026. Prioritize safety, compliance (rabies, permits), insurance documentation, and customer satisfaction."

    fun photoToFormFill(speciesTags: List<String>, damageTags: List<String>, location: String? = null, recentJobs: String = ""): String = """
Analyze wildlife job photo analysis: species=${speciesTags.joinToString()}, damage=${damageTags.joinToString()}, location=$location.
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

    fun voiceToStructuredJob(voiceText: String, currentJobContext: String = ""): String = "Convert voice note to structured wildlife job JSON: $voiceText $currentJobContext"

    fun predictTrapCheckPriority(trapHistory: String, species: String, weather: String, season: String): String = "Predict trap check priority for $species based on $weather $season $trapHistory"

    fun arMeasurementToReport(measurements: String, species: String, damageType: String): String = "Create report from $measurements for $species $damageType"
}
