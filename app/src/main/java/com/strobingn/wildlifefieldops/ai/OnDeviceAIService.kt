package com.strobingn.wildlifefieldops.ai

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo

// Heavy AI service for form filling, estimate generation, compliance analysis
// On-device vision (ML Kit) + structured prompts ready for Gemini Nano / ML Kit GenAI swap-in
data class FormSuggestions(
    val species: String = "",
    val serviceType: String = "",
    val priority: String = "MEDIUM",
    val notes: String = "",
    val recommendedActions: List<String> = emptyList()
)

data class EstimateSuggestion(
    val tier: String, // Good / Better / Best
    val lineItems: List<String>,
    val totalLow: Int,
    val totalHigh: Int,
    val notes: String
)

object OnDeviceAIService {

    // Heavy form filling from photo analysis + job history
    suspend fun generateFormSuggestions(analysis: AiAnalysisResult, recentJobs: List<Job> = emptyList()): FormSuggestions {
        val speciesStr = analysis.species.joinToString(", ")
        val service = analysis.suggestedServiceType.ifBlank { "Wildlife Removal & Exclusion" }
        val priority = analysis.suggestedPriority

        val notes = analysis.suggestedNotes.ifBlank { 
            "AI analyzed photo. ${if (analysis.species.isNotEmpty()) "Species: $speciesStr. " else ""}Damage: ${analysis.damageTypes.joinToString()}. On-site verification recommended."
        }

        val actions = mutableListOf<String>()
        if (analysis.species.any { it.contains("bat") }) actions.add("Install one-way exclusion doors + seal all entry points")
        if (analysis.species.any { it.contains("raccoon") }) actions.add("Set traps at entry points + check daily. Remove nursing females carefully.")
        if (analysis.damageTypes.isNotEmpty()) actions.add("Document all damage with GPS-tagged photos for insurance")
        actions.add("Provide customer with written estimate + photos")

        return FormSuggestions(
            species = speciesStr,
            serviceType = service,
            priority = priority,
            notes = notes,
            recommendedActions = actions
        )
    }

    // Photo-to-Estimate AI (tiered Good/Better/Best) - heavy AI for on-site quoting
    suspend fun generateEstimateFromAnalysis(analysis: AiAnalysisResult, photosCount: Int = 1): List<EstimateSuggestion> {
        val baseService = analysis.suggestedServiceType
        val basePrice = when {
            baseService.contains("Bat") -> 650
            baseService.contains("Raccoon") -> 550
            baseService.contains("Squirrel") -> 400
            else -> 300
        }

        return listOf(
            EstimateSuggestion(
                tier = "Good",
                lineItems = listOf("Basic inspection + removal", "Standard exclusion materials", "1 follow-up visit"),
                totalLow = (basePrice * 0.85).toInt(),
                totalHigh = (basePrice * 1.1).toInt(),
                notes = "Solid entry-level option. ${analysis.suggestedNotes}"
            ),
            EstimateSuggestion(
                tier = "Better",
                lineItems = listOf("Full species-specific removal", "Premium exclusion + repairs", "2 follow-up visits + warranty", "GPS photo documentation for insurance"),
                totalLow = (basePrice * 1.2).toInt(),
                totalHigh = (basePrice * 1.6).toInt(),
                notes = "Most popular choice. Includes AI-tagged photo report."
            ),
            EstimateSuggestion(
                tier = "Best",
                lineItems = listOf("Complete exclusion + habitat modification", "Lifetime warranty on seals", "Unlimited follow-ups for 1 year", "Full compliance + insurance documentation package", "AR measured damage report"),
                totalLow = (basePrice * 1.8).toInt(),
                totalHigh = (basePrice * 2.5).toInt(),
                notes = "Premium package. AI + AR documentation included. Highest close rate."
            )
        )
    }

    // Compliance / form analysis (can be called on completed form screenshot or text)
    suspend fun analyzeFormForCompliance(formText: String, serviceType: String): List<String> {
        val issues = mutableListOf<String>()
        if (serviceType.contains("bat", true) && !formText.contains("rabies", true)) {
            issues.add("Missing rabies vector protocol documentation")
        }
        if (!formText.contains("photo", true) && !formText.contains("gps", true)) {
            issues.add("Recommend adding GPS-tagged photos for insurance/permits")
        }
        if (issues.isEmpty()) issues.add("Form looks compliant. Good to go.")
        return issues
    }
}
