package com.strobingn.wildlifefieldops.data.observation

/**
 * Derived view of all evidence collected for a single [entityId].
 *
 * This is a **pure projection** of a set of [ObservationEvent]s.  It is never
 * a source of truth itself; recomputing it from the same evidence set always
 * yields the same result regardless of event arrival order.
 *
 * Maps and record displays consume [DerivedAssessment] only — never raw
 * [ObservationEvent] rows.
 */
data class DerivedAssessment(
    val entityId: String,

    /**
     * Monotonically increasing integer bumped whenever the projector algorithm
     * changes, enabling downstream consumers to detect stale cached assessments.
     */
    val projectionVersion: Int,

    /** Best-supported label given the aggregated evidence. */
    val primaryLabel: String,

    /** Ranked alternative labels and their aggregated weights. */
    val alternatives: List<LabelAlternative>,

    /**
     * 1 − max(aggregated confidence across labels).  0.0 = completely certain;
     * 1.0 = uniform distribution (total uncertainty).
     */
    val uncertainty: Float,

    /** IDs of all [ObservationEvent]s that contributed to this assessment. */
    val evidenceEventIds: Set<String>,

    val status: AssessmentStatus,

    /**
     * Non-null when at least one CONFIRMED or CORRECTED event exists.
     * This label supersedes the model consensus for display and export.
     */
    val humanVerifiedLabel: String?,

    /** Wall-clock epoch-ms when this projection was last computed. */
    val lastProjectedAt: Long,
)

data class LabelAlternative(val label: String, val weight: Float)

enum class AssessmentStatus {
    /** All contributing (non-disputed) events agree on the primary label. */
    STABLE,

    /** At least one DISPUTED event carries a different label. */
    DISPUTED,

    /**
     * Model versions differ across evidence sets, or a clock-skew flag
     * (|observedAt − uploadedAt| > 24 h) is present.
     */
    NEEDS_REVIEW,

    /** A human CONFIRMED or CORRECTED event is present; overrides model consensus. */
    HUMAN_OVERRIDE,
}
