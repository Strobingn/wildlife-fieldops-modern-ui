package com.strobingn.wildlifefieldops.ai.detection

/**
 * Sir's correction of a live or saved detection.
 * Saving is local-first. Cloud sync is a later copy of the same record.
 */
object DetectionLabels {
    val ENTRY_KINDS = listOf(
        "Gap",
        "Vent",
        "Soffit",
        "Roof return",
        "Chimney",
        "Gable vent",
        "Foundation crack",
    )

    const val LEARNED_BANNER = "Learned from your corrections"
    const val VERDICT_YES = "YES"
    const val VERDICT_NO = "NO"
    const val VERDICT_PARTLY = "PARTLY"
    const val VERDICT_UNLABELED = "UNLABELED"

    val REVIEWED = setOf(VERDICT_YES, VERDICT_NO, VERDICT_PARTLY)

    fun normalize(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")
}

data class FeedbackDraft(
    val verdict: String,
    val whatImageShows: String = "",
    val pickerLabel: String = "",
    val freeTextLabel: String = "",
    val notes: String = "",
    /** Label already shown from correction memory. Yes keeps it when Sir does not pick another type. */
    val shownLabel: String = "",
)

data class ModelOutput(
    val label: String,
    val confidence: Double,
)

data class StoredDetectionExample(
    val id: String,
    val userId: String,
    val jobId: String,
    val photoId: String,
    val photoLocalPath: String,
    val photoStoragePath: String,
    val modelLabel: String,
    val modelConfidence: Double,
    val verdict: String,
    val correctedLabel: String,
    val whatImageShows: String,
    val notes: String,
    val embedding: String,
    val createdAt: Long,
)

sealed class FeedbackBuild {
    data class Ok(val record: StoredDetectionExample) : FeedbackBuild()
    data class Rejected(val reason: String) : FeedbackBuild()
}

object DetectionFeedbackPolicy {
    const val NEED_VERDICT = "Pick Yes, No, or Partly."
    const val NEED_WHAT = "Say exactly what the image shows."

    fun validate(draft: FeedbackDraft): String? {
        val verdict = draft.verdict.trim().uppercase()
        if (verdict !in DetectionLabels.REVIEWED) return NEED_VERDICT
        if (verdict != DetectionLabels.VERDICT_YES && draft.whatImageShows.trim().isEmpty()) {
            return NEED_WHAT
        }
        return null
    }

    /** Free-text type wins over the chip. Blank means Sir did not name a type. */
    fun resolvedLabel(draft: FeedbackDraft): String =
        draft.freeTextLabel.trim().ifBlank { draft.pickerLabel.trim() }

    fun feedbackRecord(
        id: String,
        userId: String,
        jobId: String,
        photoId: String,
        photoLocalPath: String,
        photoStoragePath: String,
        modelLabel: String,
        modelConfidence: Double,
        draft: FeedbackDraft,
        embedding: String,
        createdAt: Long,
    ): FeedbackBuild {
        val reason = validate(draft) ?: ""
        if (reason.isNotEmpty()) return FeedbackBuild.Rejected(reason)
        val verdict = draft.verdict.trim().uppercase()
        val typed = resolvedLabel(draft)
        val learned = draft.shownLabel.trim()
        val corrected = when {
            typed.isNotBlank() -> typed
            verdict == DetectionLabels.VERDICT_YES && learned.isNotBlank() -> learned
            verdict == DetectionLabels.VERDICT_YES -> modelLabel.trim()
            else -> draft.whatImageShows.trim()
        }
        return FeedbackBuild.Ok(
            StoredDetectionExample(
                id = id,
                userId = userId,
                jobId = jobId,
                photoId = photoId,
                photoLocalPath = photoLocalPath,
                photoStoragePath = photoStoragePath,
                modelLabel = modelLabel.trim(),
                modelConfidence = modelConfidence.coerceIn(0.0, 1.0),
                verdict = verdict,
                correctedLabel = corrected,
                whatImageShows = draft.whatImageShows.trim(),
                notes = draft.notes.trim(),
                embedding = embedding,
                createdAt = createdAt,
            )
        )
    }

    fun unlabeledRecord(
        id: String,
        userId: String,
        jobId: String,
        photoId: String,
        photoLocalPath: String,
        modelLabel: String,
        modelConfidence: Double,
        embedding: String,
        createdAt: Long,
    ): StoredDetectionExample = StoredDetectionExample(
        id = id,
        userId = userId,
        jobId = jobId,
        photoId = photoId,
        photoLocalPath = photoLocalPath,
        photoStoragePath = "",
        modelLabel = modelLabel.trim(),
        modelConfidence = modelConfidence.coerceIn(0.0, 1.0),
        verdict = DetectionLabels.VERDICT_UNLABELED,
        correctedLabel = "",
        whatImageShows = "",
        notes = "",
        embedding = embedding,
        createdAt = createdAt,
    )

    /**
     * The label the feedback form is about: the strongest species or entry hit.
     * Entry tags have no separate score, so they use the analysis confidence.
     */
    fun primaryOutput(
        entryTypes: List<String>,
        species: List<String>,
        speciesScores: Map<String, Float>,
        confidence: Float,
        suggestionLabel: String?,
        suggestionConfidence: Float?,
    ): ModelOutput {
        var bestLabel = ""
        var bestScore = -1.0
        fun consider(label: String, score: Double) {
            val clean = label.trim()
            if (clean.isEmpty()) return
            if (score > bestScore) {
                bestLabel = clean
                bestScore = score
            }
        }
        entryTypes.forEach { consider(it, confidence.toDouble().coerceIn(0.0, 1.0)) }
        species.forEach { name ->
            consider(name, (speciesScores[name] ?: confidence).toDouble().coerceIn(0.0, 1.0))
        }
        val suggestion = suggestionLabel?.trim().orEmpty()
        if (suggestion.isNotEmpty()) {
            consider(suggestion, (suggestionConfidence ?: confidence).toDouble().coerceIn(0.0, 1.0))
        }
        if (bestLabel.isEmpty()) {
            return ModelOutput(label = "", confidence = confidence.toDouble().coerceIn(0.0, 1.0))
        }
        return ModelOutput(bestLabel, bestScore.coerceIn(0.0, 1.0))
    }
}
