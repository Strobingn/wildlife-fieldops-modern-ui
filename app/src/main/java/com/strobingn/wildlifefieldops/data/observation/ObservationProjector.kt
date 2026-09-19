package com.strobingn.wildlifefieldops.data.observation

/**
 * Deterministic, verification-aware projector.
 *
 * Computes a [DerivedAssessment] from an unordered set of [ObservationEvent]s
 * using a conservative, non-last-write-wins strategy:
 *
 *  1. Human trust hierarchy:  CORRECTED > CONFIRMED > UNREVIEWED > (DISPUTED excluded)
 *  2. Evidence is weighted by [ObservationEvent.captureQuality]; frames with
 *     quality = 0.0 are excluded from confidence aggregation (still stored).
 *  3. Commutativity: inputs are sorted by [ObservationEvent.eventId] before any
 *     ordered iteration so that identical event sets always produce identical output.
 *  4. Rare-class preservation: a human-verified rare label is never overturned by
 *     later model events, even with higher raw confidence.
 *  5. Idempotent: duplicate events (same eventId) are deduplicated before projection.
 *
 * No Android, Room, or coroutine dependencies — pure Kotlin / JVM.
 */
object ObservationProjector {

    /** Version bumped whenever the algorithm changes. */
    const val PROJECTION_VERSION = 1

    /** Clock-skew threshold in milliseconds (24 hours). */
    private const val CLOCK_SKEW_THRESHOLD_MS = 24L * 60L * 60L * 1_000L

    /**
     * Compute a [DerivedAssessment] for [entityId] from [events].
     *
     * @param events   Any collection of [ObservationEvent]s for [entityId].
     *                 Events for other entityIds are silently ignored.
     * @param nowMs    Current wall-clock epoch-ms; defaults to [System.currentTimeMillis].
     * @throws IllegalArgumentException if [events] is empty after filtering.
     */
    fun project(
        entityId: String,
        events: Collection<ObservationEvent>,
        nowMs: Long = System.currentTimeMillis(),
    ): DerivedAssessment {
        // ── 1. Filter to this entity and deduplicate by eventId ──────────────
        val deduped: List<ObservationEvent> = events
            .filter { it.entityId == entityId }
            .associateBy { it.eventId }   // last-occurrence wins on same ID (they are equal)
            .values
            .sortedBy { it.eventId }      // canonical ordering → commutativity guarantee

        require(deduped.isNotEmpty()) {
            "No ObservationEvents found for entityId='$entityId'"
        }

        val allEventIds = deduped.map { it.eventId }.toSet()

        // ── 2. Partition by verification state ──────────────────────────────
        val humanCorrected = deduped.filter { it.humanVerificationState == HumanVerificationState.CORRECTED }
        val humanConfirmed = deduped.filter { it.humanVerificationState == HumanVerificationState.CONFIRMED }
        val disputed       = deduped.filter { it.humanVerificationState == HumanVerificationState.DISPUTED }
        val unreviewed     = deduped.filter { it.humanVerificationState == HumanVerificationState.UNREVIEWED }

        // ── 3. Flags that affect status ──────────────────────────────────────
        val hasClockSkew = deduped.any {
            kotlin.math.abs(it.observedAt - it.uploadedAt) > CLOCK_SKEW_THRESHOLD_MS
        }
        val modelVersions = deduped.map { it.modelId }.toSet()
        val hasModelMix = modelVersions.size > 1

        // ── 4. Human-override path ───────────────────────────────────────────
        // Prefer CORRECTED over CONFIRMED; within each tier pick the event with
        // the highest quality-weighted confidence for the chosen label.
        val humanTier: List<ObservationEvent> = humanCorrected.ifEmpty { humanConfirmed }
        val humanVerifiedLabel: String? = if (humanTier.isNotEmpty()) {
            // Resolve ties deterministically using eventId (content hash) sort.
            val best = humanTier.maxWithOrNull(compareBy(
                // Highest quality first
                { it.captureQuality },
                // Then highest confidence for the top label
                { it.labelDistribution.values.maxOrNull() ?: 0f },
                // Deterministic tie-break
                { it.eventId },
            ))!!
            best.labelDistribution.maxByOrNull { it.value }?.key
        } else null

        // ── 5. Confidence-weighted aggregation ──────────────────────────────
        // Include unreviewed events + any CONFIRMED events in the weighted pool.
        // Exclude DISPUTED and CORRECTED events from the aggregate (they are
        // represented by humanVerifiedLabel instead).
        // Events with captureQuality == 0.0 are excluded from weighting.
        val weightingPool: List<ObservationEvent> = (unreviewed + humanConfirmed)
            .filter { it.captureQuality > 0f }

        val aggregated: Map<String, Float> = aggregateWeighted(weightingPool)

        // If the weighting pool is empty (all events are low-quality or disputed),
        // fall back to an unweighted vote across all non-disputed events.
        val effectiveAggregated: Map<String, Float> = if (aggregated.isEmpty()) {
            aggregateUnweighted(unreviewed + humanConfirmed)
        } else aggregated

        // ── 6. Primary label and alternatives ───────────────────────────────
        val primaryLabel: String = if (humanVerifiedLabel != null) {
            humanVerifiedLabel
        } else {
            effectiveAggregated.maxByOrNull { it.value }?.key
                ?: deduped.first().labelDistribution.maxByOrNull { it.value }?.key
                ?: "unknown"
        }

        val sortedAlternatives: List<LabelAlternative> = effectiveAggregated
            .entries
            .sortedByDescending { it.value }
            .map { LabelAlternative(label = it.key, weight = it.value) }

        val maxWeight = effectiveAggregated.values.maxOrNull() ?: 0f
        val uncertainty = 1f - maxWeight

        // ── 7. Assessment status ─────────────────────────────────────────────
        val status: AssessmentStatus = when {
            humanVerifiedLabel != null -> AssessmentStatus.HUMAN_OVERRIDE
            hasClockSkew || hasModelMix -> AssessmentStatus.NEEDS_REVIEW
            hasDisputedConflict(disputed, primaryLabel) -> AssessmentStatus.DISPUTED
            else -> AssessmentStatus.STABLE
        }

        return DerivedAssessment(
            entityId            = entityId,
            projectionVersion   = PROJECTION_VERSION,
            primaryLabel        = primaryLabel,
            alternatives        = sortedAlternatives,
            uncertainty         = uncertainty,
            evidenceEventIds    = allEventIds,
            status              = status,
            humanVerifiedLabel  = humanVerifiedLabel,
            lastProjectedAt     = nowMs,
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Weighted aggregation: each event contributes its [labelDistribution] scaled
     * by [ObservationEvent.captureQuality].
     */
    private fun aggregateWeighted(events: List<ObservationEvent>): Map<String, Float> {
        val acc = mutableMapOf<String, Float>()
        for (event in events) {
            val weight = event.captureQuality
            for ((label, confidence) in event.labelDistribution) {
                acc[label] = (acc[label] ?: 0f) + confidence * weight
            }
        }
        return normalise(acc)
    }

    /**
     * Unweighted aggregation: simple vote (sum of raw confidences).
     * Used only when all events have captureQuality == 0.
     */
    private fun aggregateUnweighted(events: List<ObservationEvent>): Map<String, Float> {
        val acc = mutableMapOf<String, Float>()
        for (event in events) {
            for ((label, confidence) in event.labelDistribution) {
                acc[label] = (acc[label] ?: 0f) + confidence
            }
        }
        return normalise(acc)
    }

    /** Normalises a score map so the maximum value is ≤ 1.0. */
    private fun normalise(scores: Map<String, Float>): Map<String, Float> {
        val max = scores.values.maxOrNull() ?: return scores
        return if (max <= 0f) scores else scores.mapValues { it.value / max }
    }

    /**
     * Returns true when at least one DISPUTED event carries a label that differs
     * from the agreed [primaryLabel].
     */
    private fun hasDisputedConflict(
        disputed: List<ObservationEvent>,
        primaryLabel: String,
    ): Boolean = disputed.any { event ->
        val topDisputedLabel = event.labelDistribution.maxByOrNull { it.value }?.key
        topDisputedLabel != null && topDisputedLabel != primaryLabel
    }
}
