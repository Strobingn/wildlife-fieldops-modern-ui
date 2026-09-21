package com.strobingn.wildlifefieldops.ai.species

import com.strobingn.wildlifefieldops.data.observation.DerivedAssessment
import com.strobingn.wildlifefieldops.data.observation.HumanVerificationState
import com.strobingn.wildlifefieldops.data.observation.LabelAlternative
import com.strobingn.wildlifefieldops.data.observation.ObservationEvent
import com.strobingn.wildlifefieldops.data.observation.ObservationEventFactory
import com.strobingn.wildlifefieldops.data.observation.ObservationProjector

/**
 * On-device species suggestion plus the confirm-before-commit gate.
 *
 * A [SpeciesSuggestion] is display-only. [operationalLabel] is non-null only when
 * a [HumanVerificationState.CONFIRMED] or [HumanVerificationState.CORRECTED]
 * event is in the log for that entity.
 */
data class SpeciesSuggestion(
    val primaryLabel: String,
    val confidence: Float,
    val alternatives: List<LabelAlternative>,
    val safety: SpeciesSafetyNotes.SafetyCard,
    val backendTag: String,
    val labelDistribution: Map<String, Float>,
) {
    val confidencePercent: Int get() = (confidence.coerceIn(0f, 1f) * 100f).toInt()
}

data class SpeciesRecognitionCommit(
    val inferenceEvent: ObservationEvent,
    val verificationEvent: ObservationEvent,
    val operationalLabel: String,
    val verification: HumanVerificationState,
)

object SpeciesRecognition {

    const val MIN_DISPLAY_CONFIDENCE = 0.18f

    fun suggest(
        hits: Map<String, Float>,
        backendTag: String = "tflite",
    ): SpeciesSuggestion? {
        val distribution = ObservationEventFactory.sanitizeDistribution(hits)
        val primary = ObservationEventFactory.primaryLabel(distribution) ?: return null
        val confidence = distribution[primary] ?: return null
        if (confidence < MIN_DISPLAY_CONFIDENCE) return null
        val alternatives = distribution.entries
            .sortedByDescending { it.value }
            .map { LabelAlternative(label = it.key, weight = it.value) }
        return SpeciesSuggestion(
            primaryLabel = primary,
            confidence = confidence,
            alternatives = alternatives,
            safety = SpeciesSafetyNotes.forLabel(primary),
            backendTag = backendTag,
            labelDistribution = distribution,
        )
    }

    /**
     * Persistable inference (UNREVIEWED) from a still or live frame.
     * Does **not** make the ID operational.
     */
    fun recordInference(
        entityId: String,
        suggestion: SpeciesSuggestion,
        observedAt: Long,
        deviceId: String,
        operatorId: String,
        frameHash: String,
        modelHash: String,
        mediaUri: String? = null,
        captureQuality: Float,
        geometryTrust: Float,
        modelId: String = ObservationEventFactory.DEFAULT_MODEL_ID,
        quantizerTag: String = ObservationEventFactory.DEFAULT_QUANTIZER,
        uploadedAt: Long = observedAt,
        cropHash: String = frameHash,
    ): ObservationEvent = ObservationEventFactory.inference(
        entityId = entityId,
        observedAt = observedAt,
        uploadedAt = uploadedAt,
        deviceId = deviceId,
        operatorId = operatorId,
        modelId = modelId,
        modelHash = modelHash,
        backendTag = suggestion.backendTag,
        quantizerTag = quantizerTag,
        frameHash = frameHash,
        cropHash = cropHash,
        mediaUri = mediaUri,
        labelDistribution = suggestion.labelDistribution,
        captureQuality = captureQuality,
        geometryTrust = geometryTrust,
    )

    /**
     * Technician confirmation step. [technicianLabel] matching the suggestion
     * writes CONFIRMED; any other non-blank label writes CORRECTED.
     */
    fun confirm(
        inference: ObservationEvent,
        technicianLabel: String,
        confirmedAt: Long,
        operatorId: String = inference.operatorId,
        uploadedAt: Long = confirmedAt,
    ): SpeciesRecognitionCommit {
        val label = technicianLabel.trim()
        require(label.isNotEmpty()) { "Technician confirmation requires an explicit label" }
        val suggested = ObservationEventFactory.primaryLabel(inference.labelDistribution)
        val same = suggested != null && SpeciesSafetyNotes.normalize(suggested) ==
            SpeciesSafetyNotes.normalize(label)
        val verification = if (same) {
            ObservationEventFactory.confirm(
                inference = inference,
                confirmedAt = confirmedAt,
                uploadedAt = uploadedAt,
                operatorId = operatorId,
            )
        } else {
            ObservationEventFactory.correct(
                inference = inference,
                correctedLabel = label,
                correctedAt = confirmedAt,
                uploadedAt = uploadedAt,
                operatorId = operatorId,
            )
        }
        val operational = ObservationEventFactory.primaryLabel(verification.labelDistribution)
            ?: label
        return SpeciesRecognitionCommit(
            inferenceEvent = inference,
            verificationEvent = verification,
            operationalLabel = operational,
            verification = verification.humanVerificationState,
        )
    }

    /**
     * Operational species ID for an entity. Null until a human confirms or corrects.
     */
    fun operationalLabel(
        entityId: String,
        events: Collection<ObservationEvent>,
        nowMs: Long = 0L,
    ): String? {
        val mine = events.filter { it.entityId == entityId }
        if (mine.none { it.isHumanDecision() }) return null
        val assessment = ObservationProjector.project(
            entityId = entityId,
            events = mine,
            nowMs = if (nowMs > 0L) nowMs else (mine.maxOfOrNull { it.observedAt } ?: 0L),
        )
        return operationalLabel(assessment)
    }

    fun operationalLabel(assessment: DerivedAssessment): String? =
        assessment.humanVerifiedLabel?.takeIf { it.isNotBlank() }

    fun isOperational(event: ObservationEvent): Boolean = event.isHumanDecision()

    private fun ObservationEvent.isHumanDecision(): Boolean =
        humanVerificationState == HumanVerificationState.CONFIRMED ||
            humanVerificationState == HumanVerificationState.CORRECTED
}
