package com.strobingn.wildlifefieldops.ai.fieldops

/**
 * AI may fill blanks. Anything the operator typed or explicitly cleared is kept
 * unless they tap Apply on a suggestion preview.
 */
object OperatorWins {
    fun text(existing: String, suggested: String, replace: Boolean): String {
        val suggestion = suggested.trim()
        if (suggestion.isBlank()) return existing
        if (replace) return suggestion
        return if (existing.isBlank()) suggestion else existing
    }

    /**
     * Suggest/auto-fill: only when [existing] is empty and the field is not
     * marked manual (typed or user-cleared).
     */
    fun suggest(existing: String, suggested: String, manual: Boolean): String {
        if (manual) return existing
        return text(existing, suggested, replace = false)
    }

    /** Non-null when Suggest cannot apply and the operator can tap to replace. */
    fun preview(existing: String, suggested: String, manual: Boolean): String? {
        val suggestion = suggested.trim()
        if (suggestion.isBlank()) return null
        if (!manual && existing.isBlank()) return null
        if (existing.trim() == suggestion) return null
        return suggestion
    }

    fun applyPreview(existing: String, preview: String?): String {
        val suggestion = preview?.trim().orEmpty()
        return if (suggestion.isBlank()) existing else suggestion
    }

    fun markCleared(cleared: Set<String>, key: String, value: String): Set<String> =
        if (value.isBlank()) cleared + key else cleared
}

object ManualField {
    const val SPECIES = "confirmedSpecies"
    const val CUSTOMER_NAME = "customerName"
    const val PHONE = "phone"
    const val ADDRESS = "address"
    const val SERVICE_TYPE = "serviceType"
    const val INSPECTION_TYPE = "inspectionType"
    const val LEGAL_NOTES = "legalNotes"
    const val NEXT_STEP = "nextStep"
    const val NEXT_STEP_DUE = "nextStepDueAt"
    const val WEATHER = "weatherTrapAdvice"
    const val FOLLOW_KIND = "followUpKind"
    const val FOLLOW_NOTES = "followUpNotes"
    const val FOLLOW_DUE = "followUpDueAt"
    const val WARRANTY_COVERED = "warrantyCovered"
    const val SEASONAL_TITLE = "seasonalTitle"
    const val SEASONAL_NOTES = "seasonalNotes"
    const val SEASONAL_DUE = "seasonalDueAt"
    const val NARRATIVE_FINDINGS = "findings"
    const val NARRATIVE_RECS = "recommendations"
    const val NARRATIVE_NOTES = "notes"
    const val PHOTO_SPECIES = "species"
    const val PHOTO_DAMAGE = "damage"
    const val PHOTO_ENTRY = "entry"
    const val PHOTO_EXTRA = "extra"
    const val MESSAGE_SUBJECT = "subject"
    const val MESSAGE_BODY = "body"
    const val TAX_PAID = "paid"
    const val TAX_INVOICED = "invoiced"
    const val TAX_ESTIMATED = "estimated"
    const val TAX_COLLECTED = "taxCollected"
    const val TAX_TAXABLE = "taxable"
    const val TAX_NONTAXABLE = "nontaxable"
}
